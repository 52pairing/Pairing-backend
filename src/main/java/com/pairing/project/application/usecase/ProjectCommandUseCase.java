package com.pairing.project.application.usecase;

import com.pairing.project.application.command.CreateProjectCommand;

/** 프로젝트 상태를 바꾸는 인바운드 포트. */
public interface ProjectCommandUseCase {

    /** 등록. 상태는 REGISTERED 로 시작하며 착수금 결제 후 모집이 시작된다. */
    Long create(CreateProjectCommand command);

    /**
     * 모집 시작. 착수금 결제가 끝나면 정산 도메인이 호출한다. (정책 P27)
     *
     * <p>REGISTERED 가 아니면 PJ_006. 권한 확인은 결제 쪽에서 이미 끝났으므로 여기서는 하지 않는다.
     */
    void startRecruiting(Long projectId);
}