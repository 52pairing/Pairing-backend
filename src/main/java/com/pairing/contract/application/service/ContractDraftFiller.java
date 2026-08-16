package com.pairing.contract.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.infrastructure.config.ContractDraftExecutorConfig;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * DRAFT 계약서의 자유 텍스트를 채우고 서명 대기로 넘긴다.
 *
 * <p>진입점이 둘이다 — 계약 생성 직후({@link ContractDraftListener})와 5분 주기 복구
 * ({@link ContractDraftRecoveryService}). 채우는 일은 같으므로 여기 한 곳에 둔다.
 * 나눠 적으면 폴백 조건이 두 곳에서 갈린다.
 *
 * <p><b>AI 호출이 실패하면 계약은 DRAFT 로 남는다.</b> 업무 범위가 덜 다듬어진 계약서에
 * 서명이 들어가는 것보다 낫다. 화면은 DRAFT 를 "계약서 준비 중"으로 그리고 서명 버튼을
 * 막으므로 이 상태로 놔둬도 깨지지 않는다.
 *
 * <p>다만 <b>영원히 기다리지는 않는다.</b> 키 만료·일일 한도 소진 같은 영구 장애는 재시도로
 * 풀리지 않아서, 그때까지 DRAFT 로 두면 계약이 영영 열리지 않는다. 생성 후
 * {@code give-up-after-minutes} 를 넘기면 원문을 잘라 넣고 계약을 연다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractDraftFiller {

    private static final String LINK_PREFIX = "/contracts/";
    private static final String TITLE = "계약서가 도착했습니다";
    private static final String CONTENT = "협상이 타결되어 계약서가 생성되었습니다. 내용을 확인하고 서명해 주세요.";

    private final ContractRepository contractRepository;
    private final ContractProjectReaderPort projectReaderPort;
    private final ContractDraftPort draftPort;
    private final NotificationCreateUseCase notificationCreateUseCase;
    private final ObjectMapper objectMapper;

    /** 이만큼 지나도록 못 채우면 영구 장애로 보고 원문으로 확정한다. */
    @Value("${contract.draft-recovery.give-up-after-minutes:30}")
    private long giveUpAfterMinutes;

    /**
     * 한 건을 채운다.
     *
     * <p><b>{@code @Async} 와 {@code REQUIRES_NEW} 를 여기 함께 둔다.</b> 호출자가 둘이라
     * 각자 붙이면 한쪽이 빠뜨렸을 때 조용히 다르게 돈다. 특히 스케줄러 입장에서 이 메서드는
     * 즉시 반환해야 한다 — {@code @Scheduled} 는 {@code messageBrokerTaskScheduler}(풀 1개)에서
     * 도는데, 그건 다른 배치 다섯 개와 STOMP 브로커가 함께 쓰는 스레드다. 여기서 120초를
     * 물면 매칭 회차 복구와 채팅 하트비트가 같이 멈춘다.
     *
     * <p>비동기라 예외가 호출자에게 가지 않는다. 조용히 사라지면 계약이 DRAFT 에 갇힌 원인을
     * 알 수 없으므로 여기서 직접 로그로 남긴다.
     */
    @Async(ContractDraftExecutorConfig.EXECUTOR_NAME)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fill(Long contractId) {
        fillQuietly(contractId);
    }

    /**
     * 한 건을 채우고 <b>AI 서버가 살아 있는지</b>를 함께 알려준다. 복구 배치의 선두 탐침용이다.
     *
     * @return AI 호출이 성공했거나 애초에 부를 필요가 없었으면 true.
     *         호출했는데 실패했으면 false — 뒤따르는 건들도 같은 결과일 테니 배치를 멈추라는 뜻이다.
     */
    @Async(ContractDraftExecutorConfig.EXECUTOR_NAME)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompletableFuture<Boolean> probe(Long contractId) {
        return CompletableFuture.completedFuture(fillQuietly(contractId));
    }

    private boolean fillQuietly(Long contractId) {
        try {
            return fillOne(contractId);
        } catch (Exception e) {
            log.error("계약서 문구 작성 중 예기치 못한 오류. 계약은 DRAFT 로 남는다. contractId={}",
                    contractId, e);
            return false;
        }
    }

    private boolean fillOne(Long contractId) {
        Contract contract = contractRepository.findById(contractId).orElse(null);
        // 이미 채워졌다. 리스너와 스케줄러가 겹쳐 들어온 경우다.
        if (contract == null || contract.getStatus() != ContractStatus.DRAFT) {
            return true;
        }

        ContractProjectReaderPort.ProjectContractView project =
                projectReaderPort.findForContract(contract.getProjectId());
        List<String> notes = agreedNotes(contract);

        DraftResult result = resolveDraft(contract, project);
        if (result.draft() == null) {
            return false;   // 실패했고 아직 포기할 때가 아니다. 스케줄러가 다시 온다
        }

        if (contract.completeDraft(toJson(result.draft()), specialTerms(notes, result.draft()))) {
            contractRepository.updateState(contract);
            notifyParties(contract);
        }
        return result.aiHealthy();
    }

    /**
     * 문구를 얻는다. 실패했는데 포기할 때가 아니면 {@code draft} 가 null 이다 — DRAFT 로 남긴다.
     *
     * <p>담당 업무 원문이 비면 아예 호출하지 않는다. 파이썬 {@code main_task} 가 필수라 빈 값으로
     * 부르면 422 로 튕기고, 재시도해도 영원히 같다. 프로젝트 등록 시 선택 입력이라 실제로
     * 비어 있을 수 있다.
     */
    private DraftResult resolveDraft(Contract contract,
                                     ContractProjectReaderPort.ProjectContractView project) {
        String mainTask = project.mainTask();
        if (mainTask == null || mainTask.isBlank()) {
            return new DraftResult(ContractDraftText.defaults(mainTask, project.detailScope()), true);
        }

        try {
            ContractDraftText draft = draftPort.draft(new ContractDraftPort.ContractDraftCommand(
                    contract.getId(), mainTask, project.detailScope(), agreedNotes(contract)));
            return new DraftResult(draft, true);

        } catch (Exception e) {
            if (!isGiveUpTime(contract)) {
                log.warn("계약서 문구 생성 실패. DRAFT 로 두고 다시 시도한다. contractId={}, cause={}",
                        contract.getId(), e.toString());
                return new DraftResult(null, false);
            }
            log.error("계약서 문구 생성을 {}분간 실패했다. 원문으로 확정하고 계약을 연다. contractId={}",
                    giveUpAfterMinutes, contract.getId(), e);
            return new DraftResult(ContractDraftText.defaults(mainTask, project.detailScope()), false);
        }
    }

    /** 생성 후 이만큼 지나도록 못 채웠으면 영구 장애로 본다. */
    private boolean isGiveUpTime(Contract contract) {
        LocalDateTime createdAt = contract.getCreatedAt();
        // 시각을 모르면 포기 판단을 할 수 없다. 계속 시도하는 쪽이 안전하다.
        return createdAt != null
                && createdAt.isBefore(LocalDateTime.now().minusMinutes(giveUpAfterMinutes));
    }

    /**
     * 특약 후보. <b>이벤트가 아니라 계약에서 읽는다.</b>
     *
     * <p>스케줄러는 {@code ContractCreatedEvent} 를 갖고 있지 않다. 그런데 계약을 만들 때 협상
     * SCOPE·OTHER 합의값을 줄바꿈으로 이어 {@code specialTerms} 에 이미 저장해 뒀고
     * ({@code ContractCreationService}), DRAFT 인 계약은 아직 AI 결과로 덮이기 전이라 원문
     * 그대로다. 그래서 두 진입점이 정확히 같은 값을 본다.
     */
    private List<String> agreedNotes(Contract contract) {
        String terms = contract.getSpecialTerms();
        if (terms == null || terms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(terms.split("\\R"))
                .map(String::strip)
                .filter(note -> !note.isEmpty())
                .toList();
    }

    /**
     * 계약에 저장할 특약사항. 덮어쓸 문장이 없으면 null 을 돌려주고 협상 합의 원문을 그대로 둔다.
     *
     * <p>파이썬은 합의 메모가 없을 때<b>와</b> LLM 이 빈 값을 뱉었을 때 모두
     * {@code "별도의 특약사항 없음"} 을 돌려준다. 응답만으로는 둘을 구분할 수 없으므로,
     * 합의 메모가 있었는데 그 문구가 오면 LLM 이 실패한 것으로 보고 원문을 지킨다.
     */
    private String specialTerms(List<String> agreedNotes, ContractDraftText draft) {
        if (agreedNotes == null || agreedNotes.isEmpty()) {
            return null;
        }
        return ContractDraftText.NO_SPECIAL_TERMS.equals(draft.specialTerms())
                ? null : draft.specialTerms();
    }

    /**
     * 조항 스냅샷. 직렬화가 깨져도 서명 대기로는 넘긴다 — 렌더러가 없는 칸을 기본 문구로 채운다.
     * 여기서 멈추면 계약이 DRAFT 에 갇혀 아무도 서명하지 못한다.
     */
    private String toJson(ContractDraftText draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException e) {
            log.warn("계약서 문구 직렬화 실패. 기본 문구로 진행한다.", e);
            return null;
        }
    }

    /**
     * 갑·을 모두에게 알린다. 서명 화면이 열렸다는 신호다.
     *
     * <p>실패해도 삼킨다. 알림은 부수 효과인데 여기서 예외가 나가면 같은 트랜잭션인 상태 전이까지
     * 롤백되어 계약이 DRAFT 에 갇힌다. 그러면 아무도 서명하지 못한다. 알림이 없는 계약서보다
     * 열리지 않는 계약서가 나쁘다.
     */
    private void notifyParties(Contract contract) {
        try {
            contract.getSignatures().forEach(signature -> notify(contract, signature.getAccountId()));
        } catch (Exception e) {
            log.warn("계약서 생성 알림 실패. 계약은 서명 대기로 넘어갔다. contractId={}", contract.getId(), e);
        }
    }

    private void notify(Contract contract, Long accountId) {
        notificationCreateUseCase.create(new CreateNotificationCommand(
                accountId, NotificationType.CONTRACT_CREATED, TITLE, CONTENT,
                LINK_PREFIX + contract.getId()));
    }

    /** {@code draft} 가 null 이면 이번엔 채우지 않는다. {@code aiHealthy} 는 배치를 이어갈지 가른다. */
    private record DraftResult(ContractDraftText draft, boolean aiHealthy) {
    }
}
