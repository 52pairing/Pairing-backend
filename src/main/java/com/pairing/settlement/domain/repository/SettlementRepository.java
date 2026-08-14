package com.pairing.settlement.domain.repository;

import com.pairing.settlement.application.result.MySettlementSummary;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SettlementRepository {

    Settlement save(Settlement settlement);

    Optional<Settlement> findById(Long settlementId);

    /**
     * 계약에 걸린 특정 단계의 정산. 중복 생성을 여기서 막는다.
     *
     * <p><b>{@code phase} 를 반드시 함께 건다.</b> 계약 1건에 착수금과 성공보수가 각각 붙으므로
     * {@code contractId} 만으로 찾으면 2건이 잡혀 단건 조회가 깨진다.
     */
    Optional<Settlement> findByContractIdAndPhase(Long contractId, SettlementPhase phase);

    /** 내 정산 목록. projectId / phase / status 는 null 이면 필터하지 않는다. */
    Page<Settlement> findByPayer(Long payerAccountId, Long projectId, SettlementPhase phase,
                                 SettlementStatus status, Pageable pageable);

    /**
     * 마이페이지 결제 내역 요약. <b>결제 완료만</b> 센다.
     *
     * <p>목록으로는 만들 수 없다. 페이징이라 한 페이지 몫만 더하게 되어 2페이지부터 틀린다.
     *
     * <p>낸 게 없으면 {@link MySettlementSummary#EMPTY} 다. null 을 돌려주지 않는다 —
     * 신규 가입자가 대부분 이 경우라 화면이 매번 널 검사를 하게 만들 이유가 없다.
     */
    MySettlementSummary sumPaidByPayer(Long payerAccountId);

    /**
     * 프로젝트에 걸린 <b>클라이언트</b>의 결제 대기 정산 1건.
     *
     * <p>화면의 결제 버튼이 쓴다. 계약이 체결되면 같은 프로젝트에 프리랜서 착수금이 여러 건 붙으므로
     * 역할로 좁힌다. 클라이언트 기준으로는 착수금과 성공보수가 동시에 미결제일 수 없어 단건이면 된다.
     */
    Optional<Settlement> findPayableByProjectId(Long projectId);

    /**
     * 프로젝트에 걸린 미결제 정산 전부. 납부자 구분을 가리지 않는다.
     *
     * <p>{@link #findPayableByProjectId} 는 클라이언트 1건만 찾는다. 등록 취소용이라 그때는
     * 그것뿐이어서 맞았다. 모집 기간 만료(P46)는 계약이 체결된 만큼 프리랜서 착수금이
     * 여러 건 붙어 있어 전부 봐야 한다.
     */
    List<Settlement> findAllPayableByProjectId(Long projectId);

    /**
     * 아직 내지 않은 정산이 있는지. 탈퇴 가능 여부 판정용이라 건수를 세지 않고 존재만 본다.
     *
     * <p>기준은 {@link #findPayableByProjectId} 와 같다. 결제할 수 있는 상태 = 아직 안 낸 상태다.
     */
    boolean existsUnpaidByPayer(Long payerAccountId);

    /**
     * 아직 안 낸 프리랜서 착수금이 남아 있는지.
     *
     * <p>전원이 계약하고 착수금까지 냈을 때 프로젝트를 진행중으로 넘기는 판정에 쓴다.
     * 건수가 아니라 존재만 보면 된다.
     */
    boolean existsUnpaidFreelancerDeposit(Long projectId);

    /**
     * 주어진 계약들 중 프리랜서 착수금을 이미 낸 계약의 ID.
     *
     * <p>계약 목록의 "결제 필요" 배지 판정에 쓴다. 계약마다 되물으면 페이지 크기만큼 쿼리가 늘어나
     * 한 번에 받는다. 입력에 없던 id 는 결과에도 없고, 빈 입력이면 빈 집합이다.
     */
    Set<Long> findPaidFreelancerDepositContractIds(Collection<Long> contractIds);

    /**
     * 계약별로 이 사람이 지금 결제할 정산 ID. {@code 계약 ID -> 정산 ID}.
     *
     * <p>계약 목록의 결제 버튼이 쓴다. 낼 게 없는 계약은 결과에 없다. 계약마다 되물으면 페이지
     * 크기만큼 쿼리가 늘어나 한 번에 받는다. 빈 입력이면 빈 맵이다.
     */
    Map<Long, Long> findPayableSettlementIdsByContract(Long payerAccountId,
                                                       Collection<Long> contractIds);
}
