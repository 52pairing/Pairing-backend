package com.pairing.matching.presentation.api.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.pairing.global.infrastructure.s3.CdnMappable;
import com.pairing.global.infrastructure.s3.CdnUrlSerializerModifier;
import com.pairing.global.infrastructure.s3.CdnUrlSerializerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후보 카드의 프로필 이미지가 절대 URL로 나가는지 본다.
 *
 * <p>{@link CandidateResponse} 가 {@link CdnMappable} 을 구현하지 않아 DB 의 object key
 * ("dummy/profile/freelancer-0082.png")가 그대로 나가던 버그를 막는다. 그 값을 받은 프론트의
 * {@code next/image} 는 상대경로를 파싱하지 못해 후보 카드 사진이 전부 깨졌다.
 *
 * <p>통합 테스트가 아니라 직렬화만 떼어 본다. 문제가 났던 지점이 DTO 선언 한 줄이라,
 * 후보 시딩까지 끌고 오면 정작 무엇을 지키는 테스트인지 흐려진다.
 */
class CandidateResponseCdnMappingTest {

    private static final String CDN = "https://pairing-bucket.s3.ap-northeast-2.amazonaws.com";
    private static final String OBJECT_KEY = "dummy/profile/freelancer-0082.png";

    private ObjectMapper objectMapper;
    private String originalCdnBase;

    @BeforeEach
    void setUp() {
        // CDN 루트는 JVM 전체가 공유하는 static 이다. 캐시된 스프링 컨텍스트를 쓰는 뒤 테스트가
        // 이 값을 물려받지 않도록, 원래 값을 받아 두었다가 되돌린다.
        originalCdnBase = CdnUrlSerializerTestSupport.current();
        CdnUrlSerializerTestSupport.configure(CDN);

        SimpleModule module = new SimpleModule("CdnUrlModule");
        module.setSerializerModifier(new CdnUrlSerializerModifier());
        objectMapper = new ObjectMapper().registerModule(module);
    }

    @AfterEach
    void tearDown() {
        CdnUrlSerializerTestSupport.configure(originalCdnBase);
    }

    @Test
    @DisplayName("후보 카드의 프로필 이미지가 CDN 절대 URL로 나간다")
    void candidateProfileImageBecomesAbsoluteUrl() throws Exception {
        String json = objectMapper.writeValueAsString(candidate(OBJECT_KEY));

        assertThat(json).contains(CDN + "/" + OBJECT_KEY);
    }

    @Test
    @DisplayName("프로필을 안 올린 후보는 null 그대로 나간다 — CDN 루트만 붙은 깨진 URL이 되면 안 된다")
    void nullProfileImageStaysNull() throws Exception {
        String json = objectMapper.writeValueAsString(candidate(null));

        assertThat(json).contains("\"profileImageUrl\":null");
        assertThat(json).doesNotContain(CDN);
    }

    @Test
    @DisplayName("이미 절대 URL이면 CDN 루트를 덧붙이지 않는다")
    void absoluteUrlIsLeftAlone() throws Exception {
        String external = "https://cdn.example.com/avatar.png";

        String json = objectMapper.writeValueAsString(candidate(external));

        assertThat(json).contains(external);
        assertThat(json).doesNotContain(CDN + "/https");
    }

    private CandidateResponse candidate(String profileImageUrl) {
        return new CandidateResponse(100L, 7L, "홍길동", profileImageUrl,
                null, 5, "SENIOR", 4.5, 12, List.of(), List.of("요구 스킬 97% 일치"),
                null, 6_500_000L, 1, false, false,
                CandidateResponse.Status.AVAILABLE, "선택 가능");
    }
}
