package com.pairing.contract.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.event.ContractCreatedEvent;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * DRAFT 계약서의 본문 자유 텍스트를 채우고 서명 대기로 넘긴다.
 *
 * <p>협상 타결 <b>커밋 후, 별도 스레드</b>에서 돈다. 문구 정리가 AI 서버 호출이라 사용자가
 * 협상 화면에서 그 응답을 기다리면 안 된다. 커밋 후만으로는 부족하고 {@code @Async} 까지 있어야
 * 실제로 응답이 먼저 나간다.
 *
 * <p>그래서 계약은 <b>DRAFT 로 먼저 보인다.</b> 화면은 그 상태를 "계약서 준비 중"으로 처리하고
 * 서명 버튼을 막아야 한다. 문구가 채워지면 CONTRACT_CREATED 알림이 간다.
 *
 * <p>실패하면 계약은 DRAFT 로 남고 아무도 서명하지 못한다. 업무 범위가 비어 있는 계약서에
 * 서명이 들어가는 것보다 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractDraftListener {

    private static final String LINK_PREFIX = "/contracts/";
    private static final String TITLE = "계약서가 도착했습니다";
    private static final String CONTENT = "협상이 타결되어 계약서가 생성되었습니다. 내용을 확인하고 서명해 주세요.";

    private final ContractRepository contractRepository;
    private final ContractProjectReaderPort projectReaderPort;
    private final ContractDraftPort draftPort;
    private final NotificationCreateUseCase notificationCreateUseCase;
    private final ObjectMapper objectMapper;

    /**
     * <b>{@code @Async} 가 있어야 협상 타결 응답이 AI 를 기다리지 않는다.</b>
     *
     * <p>{@code @TransactionalEventListener} 는 커밋 <i>후</i>에 돌지만 <b>같은 스레드</b>에서
     * 이어 실행된다. {@code REQUIRES_NEW} 는 트랜잭션만 분리할 뿐 스레드를 나누지 않는다.
     * 그래서 이게 없으면 계약 생성 API 가 LLM 호출이 끝날 때까지(최대 20초) 응답하지 못한다.
     *
     * <p>비동기가 되면 예외가 호출한 쪽으로 가지 않는다. 조용히 사라지면 계약이 DRAFT 에 갇혀
     * 아무도 서명하지 못하는데 원인을 알 수 없으므로, 여기서 직접 로그로 남긴다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ContractCreatedEvent event) {
        try {
            fillDraft(event);
        } catch (Exception e) {
            log.error("계약서 문구 작성에 실패했다. 계약이 DRAFT 에 남아 서명할 수 없다. contractId={}",
                    event.contractId(), e);
        }
    }

    private void fillDraft(ContractCreatedEvent event) {
        Contract contract = contractRepository.findById(event.contractId()).orElse(null);
        if (contract == null) {
            return;
        }

        ContractProjectReaderPort.ProjectContractView project =
                projectReaderPort.findForContract(event.projectId());

        ContractDraftText draft = draft(event, contract, project);

        if (!contract.completeDraft(toJson(draft), specialTerms(event.agreedNotes(), draft))) {
            return;   // 이미 처리됐다. 재시도로 두 번 들어온 경우
        }
        contractRepository.updateState(contract);

        notifyParties(contract);
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

    /**
     * 문구를 얻는다. AI 가 실패하면 원문을 잘라 쓴다 — 모양은 덜 다듬어져도 실제 업무 내용이 들어간다.
     *
     * <p>담당 업무 원문이 비면 아예 호출하지 않는다. 파이썬 쪽 {@code main_task} 가 필수라
     * 빈 값으로 부르면 422 로 튕긴다. 프로젝트 등록 시 선택 입력이라 실제로 비어 있을 수 있다.
     */
    private ContractDraftText draft(ContractCreatedEvent event, Contract contract,
                                    ContractProjectReaderPort.ProjectContractView project) {
        String mainTask = project.mainTask();

        if (mainTask == null || mainTask.isBlank()) {
            return ContractDraftText.defaults(mainTask, project.detailScope());
        }

        return draftPort.draft(new ContractDraftPort.ContractDraftCommand(
                        contract.getId(), mainTask, project.detailScope(), event.agreedNotes()))
                .orElseGet(() -> {
                    log.warn("계약서 문구 생성 실패. 원문으로 대체한다. contractId={}", contract.getId());
                    return ContractDraftText.defaults(mainTask, project.detailScope());
                });
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

}
