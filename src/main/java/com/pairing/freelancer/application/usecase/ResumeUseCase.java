package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
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

    /**
     * 희망 조건과 이력서를 <b>한 트랜잭션</b>으로 저장한다.
     *
     * <p>화면이 둘을 한 페이지에 두고 저장 버튼도 하나라서, 나눠 호출하면 앞은 저장되고 뒤가
     * 실패해 절반만 반영된 상태가 남는다. 사용자에게는 한 번의 저장이므로 서버도 한 번으로 끝낸다.
     *
     * <p>조건이 먼저 저장된다. 조건은 이력서보다 검증이 단순해서, 여기서 걸릴 문제라면
     * 이력서를 건드리기 전에 걸리는 편이 낫다.
     *
     * <p>돌려주는 값은 이력서뿐이다. 응답에 조건까지 실으면 모양이 바뀌어 기존 화면이 깨진다.
     */
    ResumeResult upsertWithCondition(UpsertConditionCommand conditionCommand,
                                     UpsertResumeCommand resumeCommand);

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
