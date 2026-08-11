package com.pairing.matching.infrastructure.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 서버가 준 404를 "후보 없음(정상)"과 "진짜 오류"로 가르는 규칙.
 *
 * <p>이 구분이 무너지면 사용자에게 나가는 안내가 뒤바뀐다. 후보가 없을 뿐인데 "일시적인 오류니
 * 잠시 후 다시 시도하세요"라고 안내하면, 몇 번을 눌러도 후보는 안 생기므로 계속 시도하게 된다.
 * 서킷브레이커가 "후보 없음"을 장애로 세는 문제도 같이 생긴다.
 */
class PythonMatchingAdapterTest {

    @Test
    @DisplayName("AI_020(추천할 후보 없음)은 장애가 아니라 정상 결과로 본다")
    void treatsCandidatePoolEmptyAsNormalResult() {
        HttpClientErrorException.NotFound notFound = notFound("""
                {"timestamp":"2026-08-11T12:00:00Z","status":404,"errorCode":"AI_020",\
                "message":"추천할 후보가 없습니다.","traceId":"abc"}""");

        assertThat(PythonMatchingAdapter.isCandidatePoolEmpty(notFound)).isTrue();
    }

    @Test
    @DisplayName("같은 404여도 다른 에러코드는 진짜 오류로 본다")
    void treatsOtherNotFoundAsError() {
        HttpClientErrorException.NotFound notFound = notFound("""
                {"timestamp":"2026-08-11T12:00:00Z","status":404,"errorCode":"AI_010",\
                "message":"포지션을 찾을 수 없습니다.","traceId":"abc"}""");

        assertThat(PythonMatchingAdapter.isCandidatePoolEmpty(notFound)).isFalse();
    }

    @Test
    @DisplayName("본문이 비어 있으면 진짜 오류로 본다(모르면 안전한 쪽으로)")
    void treatsEmptyBodyAsError() {
        assertThat(PythonMatchingAdapter.isCandidatePoolEmpty(notFound(""))).isFalse();
    }

    private HttpClientErrorException.NotFound notFound(String body) {
        return (HttpClientErrorException.NotFound) HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
