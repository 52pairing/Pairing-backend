package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.FreelancerResumeSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 프리랜서 임베딩을 지금 값으로 다시 만든다. 이력서 저장({@link ResumeUpdatedEventListener})과
 * 관리자 재색인({@link EmbeddingReindexService})이 같은 일을 하므로 여기로 모았다 — 경로마다
 * 다르게 조립하면 어디서 저장했느냐에 따라 같은 사람의 벡터가 달라진다.
 *
 * <p><b>조건(스킬·단가·근무조건) 저장은 여기를 부르지 않는다.</b> 그 값들은 임베딩 텍스트에
 * 안 들어가므로 다시 만들어도 같은 벡터가 나온다. 조건은 AI 서버가 매칭할 때
 * {@code freelancer_condition}을 직접 읽어 DB 점수로 반영하므로 저장 즉시 적용된다.
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
        String text = FreelancerEmbeddingTextBuilder.buildText(summary);
        if (text.isBlank()) {
            // AI 서버가 빈 문자열을 422로 거절한다. 여기서 걸러 "왜 실패했는지 모르는 422" 대신
            // 원인이 분명한 로그를 남긴다(자기소개·경력이 다 비면 임베딩할 내용 자체가 없다).
            log.warn("[임베딩 재생성 생략] 임베딩할 텍스트가 비어 있음. freelancerId={}", freelancerId);
            return;
        }
        matchingPort.upsertFreelancerEmbedding(freelancerId, text);
    }
}
