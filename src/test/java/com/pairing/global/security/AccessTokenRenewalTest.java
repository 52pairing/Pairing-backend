package com.pairing.global.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 슬라이딩 세션의 갱신 판정.
 *
 * <p>임계값을 넘겼는지만 보는 순수 계산이라 스프링 없이 확인한다.
 */
class AccessTokenRenewalTest {

    private static final String SECRET = "test-secret-key-for-unit-test-please-change-me-32bytes+";

    private GlobalJwtProvider provider(long accessTokenExpiration, long renewThreshold) {
        GlobalJwtProvider provider = new GlobalJwtProvider();
        ReflectionTestUtils.setField(provider, "secretKey", SECRET);
        ReflectionTestUtils.setField(provider, "accessTokenExpiration", accessTokenExpiration);
        ReflectionTestUtils.setField(provider, "accessTokenRenewThreshold", renewThreshold);
        ReflectionTestUtils.setField(provider, "refreshTokenExpiration", 604_800_000L);
        ReflectionTestUtils.setField(provider, "cookieDomain", "");
        ReflectionTestUtils.setField(provider, "cookieSecure", false);
        ReflectionTestUtils.invokeMethod(provider, "init");
        return provider;
    }

    private Claims claimsOf(GlobalJwtProvider provider) {
        return provider.parseClaims(provider.createAccessToken("7", "CLIENT", "sid-1"));
    }

    @Test
    @DisplayName("남은 수명이 임계값보다 넉넉하면 갱신하지 않는다")
    void skipsRenewalWhenPlentyOfTimeLeft() {
        // 1시간짜리 토큰을 방금 발급했고 임계값은 30분이다.
        GlobalJwtProvider provider = provider(3_600_000L, 1_800_000L);

        assertThat(provider.renewAccessTokenCookie(claimsOf(provider))).isEmpty();
    }

    @Test
    @DisplayName("남은 수명이 임계값 아래면 만료를 미룬 쿠키를 만든다")
    void renewsWhenNearExpiry() {
        // 10분짜리 토큰이라 발급 직후부터 임계값(30분) 아래다.
        GlobalJwtProvider provider = provider(600_000L, 1_800_000L);

        var cookie = provider.renewAccessTokenCookie(claimsOf(provider));

        assertThat(cookie).isPresent();
        assertThat(cookie.get().getName()).isEqualTo(GlobalJwtProvider.ACCESS_TOKEN_COOKIE);
        // 새 토큰도 같은 subject·role·sid 를 그대로 물려받는다.
        Claims renewed = provider.parseClaims(cookie.get().getValue());
        assertThat(renewed.getSubject()).isEqualTo("7");
        assertThat(renewed.get("role", String.class)).isEqualTo("CLIENT");
        assertThat(renewed.get(GlobalJwtProvider.SESSION_ID_CLAIM, String.class)).isEqualTo("sid-1");
    }

    @Test
    @DisplayName("임계값이 0이면 슬라이딩이 꺼진다")
    void thresholdZeroDisablesRenewal() {
        // 만료가 코앞이어도 갱신하지 않는다. 이 값을 모르는 옛 설정으로 뜬 환경이 여기 해당한다.
        GlobalJwtProvider provider = provider(1_000L, 0L);

        assertThat(provider.renewAccessTokenCookie(claimsOf(provider))).isEmpty();
    }
}
