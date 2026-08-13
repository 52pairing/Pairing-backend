package com.pairing.settlement.application.result;

/**
 * 마이페이지 결제 내역 요약. 결제 완료({@code PAID})만 센다.
 *
 * <p>클라이언트와 프리랜서가 같은 값을 쓰고 화면이 필요한 칸만 고른다.
 * 클라이언트는 총액·착수금·성공보수 세 칸을, 프리랜서는 성공보수액과
 * {@code successFeeProjectCount} 를 쓴다.
 *
 * <p>{@code totalAmount} 를 따로 두는 이유는 지금은 두 단계의 합이지만 위약금 같은 항목이
 * 붙으면 갈라지기 때문이다. 화면이 더하게 두면 그때 두 곳을 고쳐야 한다.
 *
 * <p>프로젝트 수는 <b>단계별</b> {@code COUNT(DISTINCT project_id)} 다. 한 프로젝트에서 계약이
 * 여러 건이면 수수료도 여러 건이라 행 수로 세면 부풀어 오른다.
 *
 * <p><b>단계를 합친 프로젝트 수는 두지 않는다.</b> 단계별 값을 더하면 착수금과 성공보수를 모두 낸
 * 프로젝트가 두 번 세어진다. 제대로 세려면 쿼리를 하나 더 써야 하는데 쓰는 화면이 없다.
 * 필요해지면 그때 {@code COUNT(DISTINCT project_id)} 를 전체 범위로 한 번 더 돌린다.
 */
public record MySettlementSummary(
        long totalAmount,
        long depositAmount,
        long successFeeAmount,
        long depositProjectCount,
        long successFeeProjectCount
) {

    public static final MySettlementSummary EMPTY = new MySettlementSummary(0, 0, 0, 0, 0);
}
