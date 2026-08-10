package com.pairing.negotiation.application.result;

import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;

import java.util.List;

/**
 * 타결된 협상의 계약 생성용 스냅샷. 계약 도메인이 협상 리포지토리를 직접 읽지 않도록 하는 서버간 조회 결과다.
 *
 * <p>{@link NegotiationView} 와 달리 <b>뷰어 계정이 필요 없고 마지노선(floor)을 담지 않는다</b>.
 * 사람에게 보여 주는 화면이 아니라 계약서에 박을 확정값만 넘기기 때문.
 *
 * <p>{@code agreedValue} 표기는 {@code NegotiationConditionCalculator} 가 조건을 만들 때 쓰는 것과 같다.
 * AMOUNT={@code "42000000"}, PERIOD={@code "4 MONTH"}, START_DATE={@code "2026-09-01"},
 * WORK_STYLE/WORK_FORM=enum 이름, SCOPE/OTHER=자유 텍스트.
 * AI 제안값도 {@code NegotiationAgreedValueNormalizer} 가 이 표기로 정규화한 뒤에만 락되므로 그대로 신뢰해도 된다.
 *
 * <p>총액은 {@code agreedAmount}(Long)를 쓰면 된다. AMOUNT 조건의 {@code agreedValue} 와 같은 값이라 파싱이 필요 없다.
 */
public record AgreedNegotiationView(
        Long negotiationId,
        Long requestId,
        Long projectId,
        Long positionId,
        Long freelancerId,
        Long agreedAmount,
        List<AgreedCondition> conditions
) {

    /** 합의된 조건 1건. AGREED 상태만 담긴다. */
    public record AgreedCondition(ConditionType conditionType, String agreedValue) {
    }

    /**
     * 타결 협상에서 AGREED 조건만 추려 만든다. 호출 전에 타결 여부를 검증할 것.
     *
     * <p>{@code Negotiation.agree()} 가 전 조건 합의를 강제하므로 정상 흐름에서 걸러질 조건은 없다.
     * 필터는 부분 타결이 생기거나 데이터가 어긋났을 때 확정 안 된 값이 계약으로 새지 않게 하는 방어다.
     */
    public static AgreedNegotiationView from(Negotiation negotiation) {
        List<AgreedCondition> agreed = negotiation.getConditions().stream()
                .filter(c -> c.getStatus() == ConditionStatus.AGREED)
                .map(c -> new AgreedCondition(c.getConditionType(), c.getAgreedValue()))
                .toList();
        return new AgreedNegotiationView(
                negotiation.getId(),
                negotiation.getRequestId(),
                negotiation.getProjectId(),
                negotiation.getPositionId(),
                negotiation.getFreelancerId(),
                negotiation.getAgreedAmount(),
                agreed);
    }
}
