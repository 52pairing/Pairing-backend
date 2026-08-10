package com.pairing.negotiation.application.service;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;
import com.pairing.negotiation.domain.repository.NegotiationMessageRepository;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import com.pairing.negotiation.exception.NegotiationErrorCode;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 프론트 연동용 협상 더미 생성. <b>개발 편의 기능이며 운영에 두지 않는다.</b>
 *
 * <p>협상은 매칭 수락으로만 생기는데 그 앞 단계(프로젝트 등록 → 모집 → 매칭 요청 → 수락)가 아직
 * 연동되지 않아, 프론트가 협상 화면을 띄울 데이터를 만들 수 없다. 이 서비스는 <b>내가 소유한
 * 프로젝트</b>에 화면 상태별 협상을 한 번에 꽂아 준다.
 *
 * <p>{@code app.dev.seed-enabled=true} 일 때만 빈으로 등록된다(기본 false). 켜져 있어도
 * <b>로그인한 본인이 소유한 프로젝트</b>에만 만들 수 있어 남의 데이터를 건드리지 못한다.
 *
 * <p>⚠️ 실서비스 오픈 전에 이 클래스와 컨트롤러를 삭제할 것.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev.seed-enabled", havingValue = "true")
public class NegotiationDevSeedService {

    /** 화면 상태별 시나리오. 프론트가 각 화면을 확인하는 데 필요한 조합이다. */
    public enum Scenario {
        /** 마지노선 입력 전(라운드 0). 화면 §3 "협상 시작" 버튼 상태 */
        BEFORE_START,
        /** AI 제안이 와서 내 응답 차례(waitingForMe=true). 화면 §4~5 승인 패널 */
        WAITING_FOR_ME,
        /** 조건 하나가 거절돼 재지시 필요. 화면 §6 재지시 패널 */
        NEEDS_REDIRECT,
        /** 전 조건 합의 → 타결. 화면 §10 */
        AGREED,
        /** 결렬. 화면 §9 */
        FAILED
    }

    private final NegotiationRepository negotiationRepository;
    private final NegotiationMessageRepository messageRepository;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final AccountRepository accountRepository;
    private final FreelancerProfileRepository freelancerProfileRepository;

    public record SeededNegotiation(Long negotiationId, Scenario scenario, String freelancerName) {
    }

    /**
     * 내 프로젝트에 시나리오별 협상을 만든다.
     *
     * @param projectId 대상 프로젝트. <b>로그인 계정이 소유한 것이어야 한다</b>
     * @param accountId 로그인 계정
     */
    public List<SeededNegotiation> seed(Long projectId, Long accountId) {
        if (!projectQueryUseCase.isOwnedBy(projectId, accountId)) {
            throw new BusinessException(NegotiationErrorCode.NOT_PARTICIPANT);
        }
        // positionId 는 협상 레코드에 보관만 되고 화면 조회에 쓰이지 않아 고정값으로 둔다.
        Long positionId = 1L;

        List<SeededNegotiation> created = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            created.add(create(projectId, positionId, scenario));
        }
        log.warn("[DEV] 협상 더미 생성: projectId={}, count={}", projectId, created.size());
        return created;
    }

    private SeededNegotiation create(Long projectId, Long positionId, Scenario scenario) {
        String name = "테스트프리_" + scenario.name();
        Long freelancerProfileId = createFreelancer(name);

        // budgetCap·freelancerMonthlyPay 는 월 단가(원)다. 프리가 상한보다 높게 불러 AMOUNT 가 쟁점이 되는 상황.
        Negotiation negotiation = Negotiation.create(
                randomId(), projectId, positionId, freelancerProfileId,
                5_000_000L, 4_000_000L, conditionsFor(scenario));
        Negotiation saved = negotiationRepository.save(negotiation);
        applyScenario(saved, scenario);
        return new SeededNegotiation(saved.getId(), scenario, name);
    }

    /** 화면에 조건 카드가 3개 뜨도록 금액·근무방식·기간을 만든다(값은 전부 문자열, 금액은 원 단위). */
    private List<NegotiationCondition> conditionsFor(Scenario scenario) {
        return List.of(
                NegotiationCondition.create(ConditionType.AMOUNT, "3200000", "4000000", 0),
                NegotiationCondition.create(ConditionType.WORK_STYLE, "ONSITE", "REMOTE", 1),
                NegotiationCondition.create(ConditionType.PERIOD, "6 MONTH", "4 MONTH", 2));
    }

    private void applyScenario(Negotiation negotiation, Scenario scenario) {
        List<NegotiationCondition> conditions = negotiation.getConditions();
        switch (scenario) {
            case BEFORE_START -> {
                // 라운드 0 그대로. 마지노선 입력 화면이 뜬다.
            }
            case WAITING_FOR_ME -> {
                submitFloors(conditions);
                negotiation.incrementRound();
                negotiationRepository.save(negotiation);
                sealAndSave(negotiation, a2aMessages(negotiation, conditions));
            }
            case NEEDS_REDIRECT -> {
                submitFloors(conditions);
                negotiation.incrementRound();
                conditions.get(0).redirect(PartyRole.CLIENT, "3300000");   // 금액 거절 → 재지시 필요
                conditions.get(1).lock("REMOTE");                          // 근무방식은 이미 합의
                negotiationRepository.save(negotiation);
                sealAndSave(negotiation, a2aMessages(negotiation, conditions));
            }
            case AGREED -> {
                submitFloors(conditions);
                negotiation.incrementRound();
                conditions.get(0).lock("3500000");
                conditions.get(1).lock("REMOTE");
                conditions.get(2).lock("6 MONTH");
                negotiation.agree(3_500_000L);
                negotiationRepository.save(negotiation);
                sealAndSave(negotiation, a2aMessages(negotiation, conditions));
            }
            case FAILED -> {
                submitFloors(conditions);
                negotiation.incrementRound();
                negotiation.fail("[DEV] 더미 결렬");
                negotiationRepository.save(negotiation);
                sealAndSave(negotiation, a2aMessages(negotiation, conditions));
            }
        }
    }

    private void submitFloors(List<NegotiationCondition> conditions) {
        conditions.get(0).submitFloor(PartyRole.CLIENT, "3500000");
        conditions.get(0).submitFloor(PartyRole.FREELANCER, "3800000");
        conditions.get(2).submitFloor(PartyRole.CLIENT, "5 MONTH");
        conditions.get(2).submitFloor(PartyRole.FREELANCER, "4 MONTH");
    }

    /** 두 대리인 대화 몇 줄. 화면 §4 로그가 비어 보이지 않게 한다. */
    private List<NegotiationMessage> a2aMessages(Negotiation negotiation, List<NegotiationCondition> conditions) {
        int round = negotiation.getTotalRound();
        Long amountId = conditions.get(0).getId();
        Long styleId = conditions.get(1).getId();
        return List.of(
                NegotiationMessage.proposal(negotiation.getId(), amountId, round, SenderType.CLIENT_AGENT,
                        "월 320만원을 제안합니다.", "등록 예산 범위 안에서 시작합니다.", "3200000"),
                NegotiationMessage.proposal(negotiation.getId(), amountId, round, SenderType.FREELANCER_AGENT,
                        "월 400만원을 요청합니다.", "경력과 시세를 반영한 금액입니다.", "4000000"),
                NegotiationMessage.proposal(negotiation.getId(), amountId, round, SenderType.CLIENT_AGENT,
                        "월 350만원 최종안입니다.", "마지노선을 고려한 절충안입니다.", "3500000"),
                NegotiationMessage.proposal(negotiation.getId(), styleId, round, SenderType.FREELANCER_AGENT,
                        "재택 근무를 제안합니다.", "업무 효율과 유연성 확보를 위해서입니다.", "REMOTE"));
    }

    /** 로그는 해시 체인으로 봉인된다. 시드도 같은 규칙을 지켜야 무결성 검증(log-integrity)이 통과한다. */
    private void sealAndSave(Negotiation negotiation, List<NegotiationMessage> messages) {
        String prevHash = messageRepository.findLatestHash(negotiation.getId())
                .orElse(NegotiationMessage.GENESIS_HASH);
        for (NegotiationMessage message : messages) {
            message.seal(prevHash);
            prevHash = message.getContentHash();
        }
        messageRepository.saveAll(messages);
    }

    /** 상대 프리랜서를 새로 만든다(이름이 화면에 보이려면 account 행이 있어야 한다). */
    private Long createFreelancer(String name) {
        Long accountId = accountRepository.save(Account.createByEmail(
                "dev-seed-" + System.nanoTime() + "@pairing.test", "seed", Role.FREELANCER,
                name, "01000000000")).getId();
        return freelancerProfileRepository.save(
                FreelancerProfile.create(accountId, LocalDate.of(1995, 1, 1))).getId();
    }

    /** requestId 는 매칭 요청 식별자다. 시드는 실제 매칭 건이 없으므로 충돌하지 않을 값을 쓴다. */
    private long randomId() {
        return ThreadLocalRandom.current().nextLong(900_000_000L, 999_999_999L);
    }
}
