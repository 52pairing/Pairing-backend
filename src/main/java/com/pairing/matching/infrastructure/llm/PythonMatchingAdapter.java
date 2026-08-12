package com.pairing.matching.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.filter.TraceIdFilter;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.result.CandidatePool;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.application.result.ScoredFreelancer;
import com.pairing.matching.exception.MatchingErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Pairing-python(FastAPI, AI 매칭 서버) 호출 어댑터.
 *
 * <p>계약은 {@code Pairing-python/README.md} "3. 스프링 ↔ AI 서버 통신 규약" 절 기준.
 * 응답 필드는 snake_case라서 로컬 record에 {@code @JsonProperty}로 매핑한다(전역 Jackson 설정은 안 건드림).
 */
@Slf4j
@Component
public class PythonMatchingAdapter implements MatchingPort {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** AI 서버(Pairing-python)의 "추천할 후보가 없습니다" 에러 코드. 장애가 아니라 정상 결과다. */
    private static final String CANDIDATE_POOL_EMPTY_CODE = "AI_020";

    private final RestClient restClient;
    private final String internalApiKey;

    public PythonMatchingAdapter(@Value("${ai.pairing-python.base-url}") String baseUrl,
                                 @Value("${ai.pairing-python.internal-api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        // 벡터 검색 + LLM 생성까지 걸리므로 읽기 타임아웃을 넉넉히 둔다(계약서 기준 60s).
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    @CircuitBreaker(name = "pythonMatchingApi", fallbackMethod = "searchCandidatesFallback")
    public CandidatePool searchCandidates(Long positionId, int limit) {
        PythonApiResponse<CandidatePoolData> response = restClient.get()
                .uri("/api/v1/embeddings/positions/{positionId}/candidates?limit={limit}", positionId, limit)
                .headers(this::withCommonHeaders)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<PythonApiResponse<CandidatePoolData>>() {
                });

        CandidatePoolData data = requireData(response);
        List<ScoredFreelancer> candidates = data.candidates().stream()
                .map(item -> new ScoredFreelancer(item.freelancerId(), item.score()))
                .toList();
        return new CandidatePool(data.positionId(), candidates);
    }

    @Override
    @CircuitBreaker(name = "pythonMatchingApi", fallbackMethod = "recommendFallback")
    public MatchingRecommendation recommend(Long positionId, int recruitCount, int poolMultiplier,
                                            List<Long> excludedFreelancerIds, long budgetCap) {
        Map<String, Object> requestBody = Map.of(
                "position_id", positionId,
                "recruit_count", recruitCount,
                "pool_multiplier", poolMultiplier,
                "excluded_freelancer_ids", excludedFreelancerIds,
                "budget_cap", budgetCap
        );

        PythonApiResponse<RecommendationData> response;
        try {
            response = restClient.post()
                    .uri("/api/v1/matchings/recommendations")
                    .headers(this::withCommonHeaders)
                    .body(requestBody)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<PythonApiResponse<RecommendationData>>() {
                    });
        } catch (HttpClientErrorException.NotFound e) {
            // "조건에 맞는 후보가 한 명도 없다"는 장애가 아니라 정상적인 결과다(AI 서버는 이걸
            // AI_020으로 알린다). 여기서 예외로 두면 서킷브레이커가 실패로 세고, 호출부는
            // MT_010("AI 서버 호출 실패")로 받아 "일시적 오류니 다시 시도하세요"라고 안내하게 된다.
            // 아무리 다시 시도해도 후보는 안 생기므로, 빈 결과로 바꿔서 호출부가 회차를
            // EXHAUSTED로 닫고 "추천할 후보가 더 없습니다"를 안내하게 한다.
            if (isCandidatePoolEmpty(e)) {
                log.info("[Pairing-python] 조건에 맞는 후보 없음 (positionId={})", positionId);
                return new MatchingRecommendation(positionId, null, List.of());
            }
            throw e;
        }

        RecommendationData data = requireData(response);
        List<RankedFreelancer> candidates = data.candidates().stream()
                .map(item -> new RankedFreelancer(item.freelancerId(), item.score(), item.reason()))
                .toList();
        return new MatchingRecommendation(data.positionId(), data.model(), candidates);
    }

    @Override
    @CircuitBreaker(name = "pythonMatchingApi", fallbackMethod = "upsertPositionEmbeddingFallback")
    public void upsertPositionEmbedding(Long positionId, String text) {
        Map<String, Object> requestBody = Map.of("position_id", positionId, "text", text);

        PythonApiResponse<EmbeddingData> response = restClient.put()
                .uri("/api/v1/embeddings/positions")
                .headers(this::withCommonHeaders)
                .body(requestBody)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<PythonApiResponse<EmbeddingData>>() {
                });
        requireData(response);
    }

    @Override
    @CircuitBreaker(name = "pythonMatchingApi", fallbackMethod = "upsertFreelancerEmbeddingFallback")
    public void upsertFreelancerEmbedding(Long freelancerId, String text) {
        Map<String, Object> requestBody = Map.of("freelancer_id", freelancerId, "text", text);

        PythonApiResponse<EmbeddingData> response = restClient.put()
                .uri("/api/v1/embeddings/freelancers")
                .headers(this::withCommonHeaders)
                .body(requestBody)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<PythonApiResponse<EmbeddingData>>() {
                });
        requireData(response);
    }

    private void withCommonHeaders(org.springframework.http.HttpHeaders headers) {
        headers.add(INTERNAL_API_KEY_HEADER, internalApiKey);
        headers.add(TRACE_ID_HEADER, TraceIdFilter.currentTraceId());
    }

    private <T> T requireData(PythonApiResponse<T> response) {
        if (response == null || response.data() == null) {
            log.warn("[Pairing-python] 응답 data가 비어 있습니다.");
            throw new BusinessException(MatchingErrorCode.AI_SERVER_CALL_FAILED);
        }
        return response.data();
    }

    /** AI 서버 장애/서킷 오픈 시 폴백. 재추천 흐름은 호출부에서 CANDIDATE_POOL_EMPTY 등으로 안내를 이어간다. */
    private CandidatePool searchCandidatesFallback(Long positionId, int limit, Throwable t) {
        log.error("[Pairing-python] 후보 조회 실패/서킷 오픈 (positionId={}, 원인: {})", positionId, t.getMessage());
        throw new BusinessException(MatchingErrorCode.AI_SERVER_CALL_FAILED);
    }

    /**
     * AI 서버가 "추천할 후보가 없습니다"(AI_020)로 답했는지. 다른 404(포지션 없음 = AI_010 등)와
     * 구분해야 한다 — 전자는 정상 결과, 후자는 진짜 오류다.
     */
    static boolean isCandidatePoolEmpty(HttpClientErrorException.NotFound e) {
        return e.getResponseBodyAsString().contains(CANDIDATE_POOL_EMPTY_CODE);
    }

    // 파라미터가 원본 메서드와 정확히 같아야(+ 끝에 Throwable) resilience4j가 폴백으로 인식한다.
    // 어긋나면 컴파일은 통과하고 서킷이 열릴 때만 터진다.
    private MatchingRecommendation recommendFallback(Long positionId, int recruitCount, int poolMultiplier,
                                                      List<Long> excludedFreelancerIds, long budgetCap,
                                                      Throwable t) {
        log.error("[Pairing-python] 추천 실패/서킷 오픈 (positionId={}, 원인: {})", positionId, t.getMessage());
        throw new BusinessException(MatchingErrorCode.AI_SERVER_CALL_FAILED);
    }

    private void upsertPositionEmbeddingFallback(Long positionId, String text, Throwable t) {
        log.error("[Pairing-python] 포지션 임베딩 저장 실패/서킷 오픈 (positionId={}, 원인: {})", positionId, t.getMessage());
        throw new BusinessException(MatchingErrorCode.AI_SERVER_CALL_FAILED);
    }

    private void upsertFreelancerEmbeddingFallback(Long freelancerId, String text, Throwable t) {
        log.error("[Pairing-python] 프리랜서 임베딩 저장 실패/서킷 오픈 (freelancerId={}, 원인: {})", freelancerId, t.getMessage());
        throw new BusinessException(MatchingErrorCode.AI_SERVER_CALL_FAILED);
    }

    private record PythonApiResponse<T>(String code, String message, T data) {
    }

    private record EmbeddingData(
            @JsonProperty("target_id") Long targetId,
            String model,
            int dimension,
            boolean skipped
    ) {
    }

    private record CandidatePoolData(
            @JsonProperty("position_id") Long positionId,
            List<CandidateItem> candidates
    ) {
    }

    private record CandidateItem(
            @JsonProperty("freelancer_id") Long freelancerId,
            double score
    ) {
    }

    private record RecommendationData(
            @JsonProperty("position_id") Long positionId,
            String model,
            List<RankedItem> candidates
    ) {
    }

    private record RankedItem(
            @JsonProperty("freelancer_id") Long freelancerId,
            double score,
            String reason
    ) {
    }
}
