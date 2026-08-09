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
     * 취소. 모집·협상·계약을 더 이상 진행하지 않는다.
     *
     * <p>소유자가 아니면 PJ_003. 이미 취소·종료됐으면 PJ_006.
     * 행은 남는다. 보존 기한(정책 P52, 5년)을 찍어두고 상태만 CANCELED 로 바꾼다.
     */
    void cancel(Long projectId, Long accountId);

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
     * 중도 종료. 진행하던 프로젝트를 중간에 닫는다. (요구사항 R30)
     *
     * <p>소유자가 아니면 PJ_003. 이미 취소·종료됐으면 PJ_006.
     * 진행중 이전이면 CANCELED, 진행중 이후면 CLOSED 가 된다.
     *
     * @return 위약금 안내 대상 여부. 계약을 맺은 인원이 있으면 true
     */
    boolean terminate(Long projectId, Long accountId);

    /**
     * 모집 종료. 남은 기간과 무관하게 닫고 프로젝트를 취소됨으로 넘긴다.
     *
     * <p>요구사항의 상태 정의에서 [취소됨] 예시가 "클라이언트가 모집을 종료했습니다" 다.
     *
     * <p>모집 중이 아니면 PJ_006. 미충원 포지션은 CLOSED 가 된다.
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
     * 종료. 성공보수 결제가 끝나면 정산 도메인이 호출한다. (정책 P30)
     *
     * <p>COMPLETION_PENDING 이 아니면 PJ_006. 권한 확인은 결제 쪽에서 이미 끝났다.
     */
    void closeProject(Long projectId);
}