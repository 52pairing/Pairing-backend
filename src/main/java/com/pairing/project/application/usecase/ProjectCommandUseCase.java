package com.pairing.project.application.usecase;

import com.pairing.project.application.command.CreateProjectCommand;
import com.pairing.project.application.command.UpdateProjectCommand;

/** 프로젝트 상태를 바꾸는 인바운드 포트. */
public interface ProjectCommandUseCase {

    /** 등록. 상태는 REGISTERED 로 시작하며 착수금 결제 후 모집이 시작된다. */
    Long create(CreateProjectCommand command);

    /**
     * 수정. 등록 시 입력한 값을 전부 바꾼다. (요구사항 R32)
     *
     * <p>소유자가 아니면 PJ_003. 취소·종료된 프로젝트는 PJ_006.
     * 착수금 결제 후에는 인원·포지션 구성(PJ_007·PJ_008)과 예산(PJ_011)이 잠긴다.
     */
    void update(UpdateProjectCommand command);

    /**
     * 완료 처리. 진행중 -> 완료 대기. 성공보수 정산이 함께 만들어진다. (정책 P30)
     *
     * <p>소유자가 아니면 PJ_003. 진행중이 아니면 PJ_006.
     * 종료로 가려면 만들어진 성공보수를 결제해야 한다.
     *
     * <p>만들어진 정산 ID 는 따로 돌려주지 않는다. 상세 조회의 payableSettlementId 가 곧 그 값이다.
     */
    void complete(Long projectId, Long accountId);

    /**
     * 등록 취소. 잘못 등록한 프로젝트를 내린다.
     *
     * <p>소유자가 아니면 PJ_003. 등록 완료(착수금 결제 전)가 아니면 PJ_015.
     * 프로젝트는 CANCELED 가 되고 미결제 착수금 정산도 함께 취소된다.
     */
    void cancelRegistration(Long projectId, Long accountId);

    /**
     * 모집 종료. 남은 기간과 무관하게 닫고 프로젝트를 취소됨으로 넘긴다.
     *
     * <p>요구사항의 상태 정의에서 [취소됨] 예시가 "클라이언트가 모집을 종료했습니다" 다.
     *
     * <p>모집 중이 아니면 PJ_014. 미충원 포지션은 CLOSED 가 된다.
     */
    void closeRecruit(Long projectId, Long accountId);

    /**
     * 모집 기간 연장. 1주 단위, 최대 2회. (정책 P46)
     *
     * <p>모집 중이 아니면 PJ_006, 상한을 넘기면 PJ_010.
     */
    void extendRecruit(Long projectId, Long accountId);

    /**
     * 모집 시작. 착수금 결제가 끝나면 정산 도메인이 호출한다. (정책 P27)
     *
     * <p>REGISTERED 가 아니면 PJ_006. 권한 확인은 결제 쪽에서 이미 끝났으므로 여기서는 하지 않는다.
     */
    void startRecruiting(Long projectId);

    /**
     * 협상 시작. 프리랜서가 매칭 요청을 수락하면 매칭 도메인이 호출한다. (요구사항 1227)
     *
     * <p>여러 명을 모집하면 인원 수만큼 들어온다. 이미 앞선 단계면 아무 일도 하지 않는다.
     * 취소·종료된 프로젝트면 PJ_012. 매칭 수락과 같은 트랜잭션에서 부르면 함께 롤백된다.
     */
    void startNegotiating(Long projectId);

    /**
     * 계약 대기. 계약서가 만들어지면 계약 도메인이 호출한다. (요구사항 1233)
     *
     * <p>전이 규칙은 {@link #startNegotiating} 과 같다.
     */
    void awaitContract(Long projectId);

    /**
     * 인원별 진행 단계에 맞춰 대표 상태를 다시 맞춘다. 협상 결렬·거절·기한 만료 뒤 매칭이 호출한다.
     *
     * <p>{@link #startNegotiating} 과 달리 뒤로도 간다. 둘 다 없으면 모집중으로 돌아간다.
     * 진행중 이후·취소·종료면 아무 일도 하지 않는다.
     *
     * @param hasContractPending 계약 대기 이상인 요청이 하나라도 있는가 (종결 상태는 제외)
     * @param hasNegotiating     수락·협상중인 요청이 하나라도 있는가
     */
    void syncStage(Long projectId, boolean hasContractPending, boolean hasNegotiating);

    /**
     * 인원 확정. 양측 서명이 끝나면 계약 도메인이 호출한다.
     *
     * <p>필요 인원이 모두 확정되면 진행중으로 넘어간다. 아니면 상태를 그대로 둔다. (요구사항 1241)
     * 포지션이 없으면 PJ_002, 이미 마감된 직군이면 PJ_013, 취소·종료된 프로젝트면 PJ_012.
     */
    void confirmPosition(Long positionId);

    /**
     * 진행중 전환. 프리랜서 착수금 수수료가 결제되면 정산 도메인이 호출한다. (P27)
     *
     * <p>인원이 다 차지 않았으면 아무 일도 하지 않는다. 서명만으로는 진행중이 되지 않는다.
     *
     * @return 이번 호출로 실제 넘어갔으면 true. 인원별 상태를 함께 옮길지 판단하는 데 쓴다
     */
    boolean startProgress(Long projectId);

    /**
     * 종료. 성공보수 결제가 끝나면 정산 도메인이 호출한다. (정책 P30)
     *
     * <p>COMPLETION_PENDING 이 아니면 PJ_006. 권한 확인은 결제 쪽에서 이미 끝났다.
     */
    void closeProject(Long projectId);
}