package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 프리랜서 임베딩을 지금 값으로 다시 만든다. 이력서 저장({@link ResumeUpdatedEventListener})과
 * 조건 저장({@link ConditionUpdatedEventListener}), 관리자 재색인({@link EmbeddingReindexService})이
 * 모두 같은 일을 하므로 여기로 모았다 — 경로마다 다른 텍스트를 올리면 어디서 저장했느냐에 따라
 * 같은 사람의 벡터가 달라진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FreelancerEmbeddingRefresher {

    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final MatchingPort matchingPort;

    void refreshByAccountId(Long accountId) {
        refreshByFreelancerId(freelancerDirectoryPort.resolveFreelancerId(accountId));
    }

    void refreshByFreelancerId(Long freelancerId) {
        FreelancerResumeSummary summary = freelancerDirectoryPort.findResumeSummary(freelancerId);
        String text = FreelancerEmbeddingTextBuilder.buildText(summary, findConditionOrNull(freelancerId));
        if (text.isBlank()) {
            // AI 서버가 빈 문자열을 422로 거절한다. 여기서 걸러 "왜 실패했는지 모르는 422" 대신
            // 원인이 분명한 로그를 남긴다(자기소개·경력·조건이 다 비면 임베딩할 내용 자체가 없다).
            log.warn("[임베딩 재생성 생략] 임베딩할 텍스트가 비어 있음. freelancerId={}", freelancerId);
            return;
        }
        matchingPort.upsertFreelancerEmbedding(freelancerId, text);
    }

    /**
     * 조건을 아직 등록 안 한 프리랜서는 {@code findCondition}이 FREELANCER_NOT_FOUND를 던진다.
     * 그걸 그대로 올리면 이력서만 쓴 사람의 임베딩이 통째로 안 만들어져서 후보에 영영 안 잡힌다 —
     * 조건 없이 이력서만으로도 벡터는 만든다.
     */
    private FreelancerConditionResponse findConditionOrNull(Long freelancerId) {
        try {
            return freelancerDirectoryPort.findCondition(freelancerId);
        } catch (BusinessException e) {
            log.debug("[임베딩 재생성] 조건 미등록 프리랜서라 이력서만으로 조립. freelancerId={}", freelancerId);
            return null;
        }
    }
}
