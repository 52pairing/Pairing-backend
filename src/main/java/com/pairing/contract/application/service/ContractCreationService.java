package com.pairing.contract.application.service;

import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.application.usecase.ContractCreationUseCase;
import com.pairing.contract.domain.model.AgreedTerms;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.negotiation.application.result.AgreedNegotiationView;
import com.pairing.negotiation.application.usecase.NegotiationQueryUseCase;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 협상 타결 -&gt; 표준계약서 생성. (요구사항 44행)
 *
 * <p>협상은 서로 맞지 않는 항목만 다룬다. 그래서 합의 조건에 없는 값은 프로젝트 등록값이 그대로
 * 계약서에 들어간다. 아래 모든 항목이 "합의값 우선, 없으면 프로젝트" 순서다.
 *
 * <p><b>금액</b>은 {@code agreedAmount}(월 단가)만 받아 총액을 개월 수로 곱해 만든다.
 * 협상 담당 확인 결과 2026-08-09 부로 월 단가로 통일됐다. 총액에서 역산하면 나눠떨어지지 않을 때
 * 원 단위가 어긋나므로 반대 방향으로 계산하지 않는다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ContractCreationService implements ContractCreationUseCase {

    /** 주 단위 계약을 개월로 바꿀 때 쓴다. 4주 = 1개월(매칭 도메인과 같은 규칙, 올림·최소 1). */
    private static final double WEEKS_PER_MONTH = 4.0;

    private final ContractRepository contractRepository;
    private final NegotiationQueryUseCase negotiationQueryUseCase;
    private final ContractProjectReaderPort projectReaderPort;
    private final ContractPartyReaderPort partyReaderPort;
    private final ProjectCommandUseCase projectCommandUseCase;

    @Override
    public Long createFromNegotiation(Long negotiationId) {
        // 협상 1건당 계약 1건. 재호출·재시도로 두 번 만들어지지 않게 먼저 막는다.
        var existing = contractRepository.findByNegotiationId(negotiationId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        AgreedNegotiationView agreed = negotiationQueryUseCase.getAgreedForContract(negotiationId);
        AgreedTerms terms = toTerms(agreed);

        ContractProjectReaderPort.ProjectContractView project =
                projectReaderPort.findForContract(agreed.projectId());

        int months = resolveMonths(terms, project);
        LocalDate startDate = resolveStartDate(terms, project);

        Contract contract = Contract.create(
                agreed.negotiationId(),
                agreed.projectId(),
                agreed.positionId(),
                project.clientProfileId(),
                agreed.freelancerId(),
                requireAccountId(partyReaderPort.findClientAccountId(project.clientProfileId())),
                requireAccountId(partyReaderPort.findFreelancerAccountId(agreed.freelancerId())),
                requireAmount(agreed.agreedAmount()),
                months,
                startDate,
                startDate.plusMonths(months).minusDays(1),
                terms.workStyle().orElse(project.workStyle()),
                terms.workForm().orElse(project.workForm()),
                project.workLocation(),
                specialTerms(terms));

        Long contractId = contractRepository.save(contract).getId();

        // 계약서가 만들어지면 프로젝트는 계약 대기로 넘어간다. 실패하면 계약 생성도 함께 롤백한다.
        projectCommandUseCase.awaitContract(agreed.projectId());

        return contractId;
    }

    /** 협상 enum 을 계약 도메인으로 들이지 않으려고 이름만 넘긴다. */
    private AgreedTerms toTerms(AgreedNegotiationView agreed) {
        Map<String, String> values = agreed.conditions().stream()
                .filter(c -> c.agreedValue() != null)
                .collect(Collectors.toMap(
                        c -> c.conditionType().name(),
                        AgreedNegotiationView.AgreedCondition::agreedValue,
                        (first, second) -> first,
                        LinkedHashMap::new));

        return AgreedTerms.of(values);
    }

    /**
     * 계약 개월 수. 총액 계산과 종료일에 쓴다.
     *
     * <p>프로젝트의 {@code periodValue}/{@code periodUnit} 이 NOT NULL 이라 합의값이 없어도 항상 구해진다.
     */
    private int resolveMonths(AgreedTerms terms, ContractProjectReaderPort.ProjectContractView project) {
        return terms.period()
                .map(p -> toMonths(p.value(), p.unit()))
                .orElseGet(() -> toMonths(project.periodValue(), project.periodUnit()));
    }

    private int toMonths(int value, PeriodUnit unit) {
        if (unit == PeriodUnit.WEEK) {
            return Math.max(1, (int) Math.ceil(value / WEEKS_PER_MONTH));
        }
        return Math.max(1, value);
    }

    /**
     * 착수일. 합의값 -&gt; 프로젝트 시작 희망일 -&gt; 체결 시도일 순으로 정한다.
     *
     * <p>{@code contract.start_date} 가 NOT NULL 이라 빈 채로 둘 수 없다. 시작일 협의 가능으로
     * 등록하면 프로젝트 희망일이 비어 있을 수 있어 마지막 폴백이 필요하다.
     */
    private LocalDate resolveStartDate(AgreedTerms terms,
                                       ContractProjectReaderPort.ProjectContractView project) {
        return terms.startDate()
                .or(() -> java.util.Optional.ofNullable(project.startDesiredDate()))
                .orElseGet(LocalDate::now);
    }

    /** 업무 범위·기타 합의 내용을 특약사항으로 옮긴다. 둘 다 없으면 특약 없음이다. */
    private String specialTerms(AgreedTerms terms) {
        String scope = terms.scope().orElse(null);
        String other = terms.other().orElse(null);

        if (scope == null) {
            return other;
        }
        return other == null ? scope : scope + System.lineSeparator() + other;
    }

    private Long requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        return accountId;
    }

    private Long requireAmount(Long agreedAmount) {
        if (agreedAmount == null || agreedAmount <= 0) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        return agreedAmount;
    }
}
