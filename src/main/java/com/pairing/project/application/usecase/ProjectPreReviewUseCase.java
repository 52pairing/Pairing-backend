package com.pairing.project.application.usecase;

import com.pairing.project.application.command.PreReviewCommand;
import com.pairing.project.application.result.PreReviewResult;

/**
 * 프로젝트 사전 검수. (정책 P02)
 *
 * <p>후보가 부족해도 등록·결제를 막지 않는다. 판단은 클라이언트가 한다.
 */
public interface ProjectPreReviewUseCase {

    /** 등록 전 검수. 화면이 입력 중인 조건으로 집계한다. */
    PreReviewResult review(PreReviewCommand command);

    /** 등록 후 재검수. 저장된 포지션으로 다시 집계한다. 소유자가 아니면 PJ_003. */
    PreReviewResult reviewRegistered(Long projectId, Long accountId);
}
