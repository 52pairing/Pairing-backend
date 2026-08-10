package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.UpsertResumeCommand;
import com.pairing.freelancer.application.result.ResumeResult;

import java.util.List;
import java.util.Optional;

public interface ResumeUseCase {

    /** 등록하지 않았으면 empty. */
    Optional<ResumeResult> findMyResume(Long accountId);

    /** 없으면 생성하고 있으면 전체 교체한다. */
    ResumeResult upsert(UpsertResumeCommand command);

    /** 이력서를 등록한 모든 계정 ID. 매칭 도메인의 임베딩 일괄 재색인이 쓴다. */
    List<Long> findAllAccountIdsWithResume();
}
