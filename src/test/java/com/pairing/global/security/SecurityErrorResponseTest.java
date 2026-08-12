package com.pairing.global.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 실패 응답이 어디서 발생하든 동일한 ErrorResponse 형식으로 나가는지 검증한다.
 * (필드명이 code/errorCode로 갈리면 프론트엔드가 에러 파싱을 여러 번 분기해야 한다)
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityErrorResponseTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("명시적으로 열지 않은 경로는 토큰 없이 호출하면 401이다 (fail-closed)")
    void unlistedPathRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/anything-not-listed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_006"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("잘못된 토큰도 같은 ErrorResponse 형식으로 401을 반환한다")
    void invalidTokenUsesSameShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/anything-not-listed")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_010"))
                .andExpect(jsonPath("$.traceId").exists())
                .andReturn();

        // 헤더로 들어온 토큰은 쿠키와 무관하다. Swagger나 스크립트에서 낡은 Bearer 토큰을
        // 한 번 잘못 보낸 것 때문에 같은 브라우저의 멀쩡한 로그인 쿠키가 날아가면 안 된다.
        assertThat(result.getResponse().getCookies()).isEmpty();
    }

    @Test
    @DisplayName("쿠키에 담긴 토큰이 유효하지 않으면 그 쿠키를 만료시킨다")
    void invalidCookieTokenIsExpired() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/anything-not-listed")
                        .cookie(new Cookie("accessToken", "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("GLOBAL_010"))
                .andReturn();

        // 서명이 깨진 토큰은 시간이 지나도 유효해지지 않는다. 쿠키에 남겨두면 모든 요청이
        // 같은 401을 받아 사용자가 로그인 화면에서 빠져나올 수 없다.
        Cookie cleared = result.getResponse().getCookie("accessToken");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("401 응답의 traceId는 X-Trace-Id 헤더와 일치한다 (TraceIdFilter가 시큐리티보다 먼저 실행)")
    void traceIdMatchesResponseHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/anything-not-listed"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String header = result.getResponse().getHeader("X-Trace-Id");
        assertThat(header).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains("\"traceId\":\"" + header + "\"");
    }

    @Test
    @DisplayName("공개로 지정한 데모 엔드포인트는 토큰 없이 접근된다")
    void permittedPathIsAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/examples/1"))
                .andExpect(status().isOk());
    }
}
