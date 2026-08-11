package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.result.ResumeDraftResult;
import com.pairing.freelancer.application.result.ResumeResult;

import java.util.List;
import java.util.Optional;

public interface ResumeUseCase {

    /** 등록하지 않았으면 empty. */
    Optional<ResumeResult> findMyResume(Long accountId);

    /** 없으면 생성하고 있으면 전체 교체한다. 등록에 성공하면 임시 저장 초안은 지워진다. */
    ResumeResult upsert(UpsertResumeCommand command);

    /** 이력서를 등록한 모든 계정 ID. 매칭 도메인의 임베딩 일괄 재색인이 쓴다. */
    List<Long> findAllAccountIdsWithResume();

    /**
     * 작성 중인 내용을 임시 저장한다. 계정당 1건이고 저장할 때마다 덮어쓴다.
     *
     * <p>필수값을 보지 않는다. 절반만 채운 상태로도 저장되는 게 목적이라, 검증은 {@link #upsert} 에서만 한다.
     */
    ResumeDraftResult saveDraft(Long accountId, String payload);

    /** 임시 저장한 초안. 없으면 empty. */
    Optional<ResumeDraftResult> findMyDraft(Long accountId);
}
