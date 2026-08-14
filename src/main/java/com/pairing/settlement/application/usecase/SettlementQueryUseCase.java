package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.result.MySettlementSummary;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Map;

public interface SettlementQueryUseCase {

    /** 납부자 본인만 열람할 수 있다. 없으면 ST_001, 남의 것이면 ST_002. */
    SettlementResult getByIdForPayer(Long settlementId, Long accountId);

    /**
     * 내가 납부자인 정산 목록. projectId / phase / status 는 null 이면 필터하지 않는다.
     *
     * <p>{@code projectId} 는 프로젝트 상세가 그 프로젝트에서 낸 수수료만 보여줄 때 쓴다.
     * 없으면 전체 목록을 받아 프론트가 걸러야 하는데, 페이징에 잘려 정확하지 않다.
     */
    Page<SettlementResult> findMine(Long accountId, Long projectId, SettlementPhase phase,
                                    SettlementStatus status, Pageable pageable);

    /**
     * 마이페이지 결제 내역 요약. 화면 상단 카드와 요약 줄에 쓴다.
     *
     * <p><b>결제 완료만 센다.</b> 문구가 "총 납부 수수료"라 실제로 낸 것만 세야 한다.
     *
     * <p>목록으로는 못 만든다. 페이징이라 한 페이지 몫만 더하게 되어 2페이지부터 틀린다.
     *
     * <p>클라이언트와 프리랜서가 같은 값을 받아 필요한 칸만 고른다. 클라이언트는 총액·착수금·
     * 성공보수 세 칸을, 프리랜서는 성공보수액과 프로젝트 수를 쓴다. 탭을 바꿔도 이 값은
     * 안 바뀌므로 화면 진입 시 한 번만 부르면 된다.
     */
    MySettlementSummary getMySummary(Long accountId);

    /**
     * 아직 내지 않은 정산이 하나라도 있는지. 회원 탈퇴 가능 여부 판정에 쓴다.
     *
     * <p>PENDING · OVERDUE · FAILED 를 미결제로 본다. 결제 실패는 다시 시도할 수 있어
     * 아직 내지 않은 돈이다. CANCELED 는 낼 이유가 사라진 건이라 제외한다.
     *
     * <p>위약금은 아직 도메인이 없어 보지 않는다. 생기면 여기에 조건을 더한다.
     */
    boolean hasUnpaidSettlement(Long accountId);

    /**
     * 계약별로 내가 지금 결제할 정산 ID. {@code 계약 ID -> 정산 ID}.
     *
     * <p>계약 목록의 결제 버튼이 쓴다. 배지만으로는 어느 정산을 결제할지 알 수 없어 ID 가 필요하다.
     * 낼 게 없는 계약은 결과에 들어가지 않는다.
     *
     * <p>{@code payerAccountId} 로 좁히므로 남의 정산이 나오지 않는다. 계약에 걸린 정산은 전부
     * 프리랜서 몫이라 클라이언트가 부르면 빈 맵이다.
     */
    Map<Long, Long> findPayableSettlementIdsByContract(Long payerAccountId,
                                                       Collection<Long> contractIds);
}
