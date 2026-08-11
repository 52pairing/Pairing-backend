package com.pairing.contract.application.service;

import com.pairing.contract.application.event.ContractCreatedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 협상 타결 -&gt; 표준계약서 생성. (요구사항 44행)
 *
 * <p>협상은 서로 맞지 않는 항목만 다룬다. 그래서 합의 조건에 없는 값은 프로젝트 등록값이 그대로
 * 계약서에 들어간다. 아래 모든 항목이 "합의값 우선, 없으면 프로젝트" 순서다.
 *
 * <p><b>금액</b>은 {@code agreedAmount}(월 단가)만 받아 총액을 개월 수로 곱해 만든다.
 * 협상 담당 확인 결과 2026-08-09 부로 월 단가로 통일됐다. 총액에서 역산하면 나눠떨어지지 않을 때
 * 원 단위가 어긋나므로 반대 방향으로 계산하지 않는다.
 *
 * <p><b>근무지</b>는 갑의 기업 주소를 여기서 굳힌다. 프로젝트 등록에는 주소 입력란이 없고
 * 클라이언트 가입 시 받은 값이 정본이다. 조회할 때마다 다시 읽으면 회사가 이사한 뒤에
 * 이미 서명된 계약서의 근무지가 바뀐다. 재택이면 {@code Contract.create} 가 알아서 버린다.
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
    private final ApplicationEventPublisher eventPublisher;

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

        ContractPartyReaderPort.ClientParty client = partyReaderPort.findClient(project.clientProfileId());
        ContractPartyReaderPort.FreelancerParty freelancer =
                partyReaderPort.findFreelancer(agreed.freelancerId());

        Contract contract = Contract.create(
                agreed.negotiationId(),
                agreed.projectId(),
                agreed.positionId(),
                project.clientProfileId(),
                agreed.freelancerId(),
                requireAccountId(client.accountId()),
                requireAccountId(freelancer.accountId()),
                requireAmount(agreed.agreedAmount()),
                months,
                startDate,
                startDate.plusMonths(months).minusDays(1),
                terms.workStyle().orElse(project.workStyle()),
                terms.workForm().orElse(project.workForm()),
                client.address(),
                specialTerms(terms));

        Long contractId = saveOnce(contract, negotiationId);

        // 계약서가 만들어지면 프로젝트는 계약 대기로 넘어간다. 실패하면 계약 생성도 함께 롤백한다.
        projectCommandUseCase.awaitContract(agreed.projectId());

        // 본문 문구 정리는 AI 서버를 호출한다. 커밋 후로 미뤄 협상 타결이 AI 응답에 묶이지 않게 한다.
        eventPublisher.publishEvent(
                new ContractCreatedEvent(contractId, agreed.projectId(), agreedNotes(terms)));

        return contractId;
    }

    /**
     * 저장. 같은 협상으로 동시에 두 번 들어오면 진 쪽이 이긴 쪽 계약을 돌려준다.
     *
     * <p>맨 앞의 존재 확인은 순차 재호출만 막는다. 협상이 타결 처리를 비동기 잡으로 돌리고
     * 그 잡이 재시도되면 두 요청이 나란히 확인을 통과해 INSERT 를 두 번 시도할 수 있고,
     * {@code uk_contract_negotiation} 에 걸린다. 협상 1건당 계약 1건이라는 결과는 같으므로
     * 예외로 만들지 않는다.
     */
    private Long saveOnce(Contract contract, Long negotiationId) {
        try {
            return contractRepository.save(contract).getId();
        } catch (DataIntegrityViolationException e) {
            return contractRepository.findByNegotiationId(negotiationId)
                    .map(Contract::getId)
                    .orElseThrow(() -> e);
        }
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

    /**
     * 업무 범위·기타 합의 내용을 특약사항으로 옮긴다. 둘 다 없으면 특약 없음이다.
     *
     * <p>여기 담기는 것은 <b>원문</b>이다. AI 가 다듬은 문장은 커밋 후에 덮어쓰며, 실패하면
     * 이 원문이 그대로 계약서에 남는다.
     */
    private String specialTerms(AgreedTerms terms) {
        String scope = terms.scope().orElse(null);
        String other = terms.other().orElse(null);

        if (scope == null) {
            return other;
        }
        return other == null ? scope : scope + System.lineSeparator() + other;
    }

    /** 같은 합의값을 AI 에는 항목별로 넘긴다. 비어 있으면 파이썬이 특약 없음으로 확정한다. */
    private List<String> agreedNotes(AgreedTerms terms) {
        return Stream.of(terms.scope(), terms.other())
                .flatMap(Optional::stream)
                .toList();
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
