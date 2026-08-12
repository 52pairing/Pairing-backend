package com.pairing.contract.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 계약관리 화면의 탭.
 *
 * <p>"서명 대기"와 "상대방 서명 대기"는 <b>계약 상태가 둘 다 {@code SIGN_PENDING} 이라</b>
 * {@link ContractStatus} 만으로 가를 수 없다. 내 서명 행의 상태를 함께 봐야 갈린다.
 * 그래서 상태 필터와 별개로 이 탭이 있다.
 *
 * <p><b>기준이 "내 서명"이라 역할을 가리지 않는다.</b> 클라이언트가 부르면 갑 서명, 프리랜서가
 * 부르면 을 서명으로 자동으로 갈리므로 화면마다 다른 파라미터를 두지 않아도 된다.
 *
 * <p>{@code DRAFT} 는 {@link #ALL} 에만 들어간다. AI 가 계약서 문구를 채우는 2~5초짜리
 * 과도기라 서명할 수 없는데, 서명 대기 탭에 넣으면 눌러도 아무 일이 일어나지 않는다.
 *
 * <p><b>화면 둘이 이 목록을 나눠 쓴다.</b> 클라이언트 계약관리는 서명 진행 상황을 보고,
 * 프리랜서 내 계약은 계약이 어느 단계까지 갔는지를 본다. 그래서 값이 겹치지 않는다.
 *
 * <pre>
 * 클라이언트  ALL · AWAITING_ME · AWAITING_COUNTERPART · CONCLUDED
 * 프리랜서    ALL · AWAITING_ME · IN_PROGRESS · SETTLEMENT_PENDING · COMPLETED
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum ContractTab {

    /** 전체. 상태를 가리지 않는다. */
    ALL("전체", null, List.of(ContractStatus.values())),

    /** 내가 아직 서명하지 않았다. 이 탭이 곧 할 일 목록이다. */
    AWAITING_ME("서명 대기", SignatureStatus.PENDING, List.of(ContractStatus.SIGN_PENDING)),

    /** 나는 서명했고 상대를 기다린다. */
    AWAITING_COUNTERPART("상대방 서명 대기", SignatureStatus.SIGNED,
            List.of(ContractStatus.SIGN_PENDING)),

    /** 양측 서명이 끝났다. 체결 이후 단계를 모두 포함한다. */
    CONCLUDED("체결 완료", null,
            List.of(ContractStatus.SIGNED, ContractStatus.IN_PROGRESS,
                    ContractStatus.COMPLETION_PENDING, ContractStatus.COMPLETED)),

    /**
     * 진행 중. 체결됐고 아직 안 끝났다.
     *
     * <p>{@code SIGNED} 를 함께 넣는 이유는, 체결만 되고 착수금 수수료를 아직 안 낸 계약이
     * 화면상 "결제하면 시작됩니다" 로 이 탭에 놓이기 때문이다. 상태 하나로는 못 가른다.
     */
    IN_PROGRESS("진행 중", null,
            List.of(ContractStatus.SIGNED, ContractStatus.IN_PROGRESS)),

    /** 정산 대기. 클라이언트가 완료 처리했고 성공보수 수수료가 남았다. */
    SETTLEMENT_PENDING("정산 대기", null, List.of(ContractStatus.COMPLETION_PENDING)),

    /** 완료. 양측 성공보수까지 끝나 프로젝트가 닫혔다. */
    COMPLETED("완료", null, List.of(ContractStatus.COMPLETED));

    private final String label;

    /** 내 서명 행이 이 상태여야 한다. null 이면 서명 상태를 보지 않는다. */
    private final SignatureStatus mySignatureStatus;

    /**
     * 계약이 이 상태들 중 하나여야 한다.
     *
     * <p>쿼리의 {@code IN} 절에 그대로 들어가므로 <b>비우지 않는다.</b> 빈 목록이면
     * {@code IN ()} 이 되어 DB 가 거부한다. 조건을 걸지 않으려면 전체 상태를 담는다.
     */
    private final List<ContractStatus> statuses;
}
