package com.pairing.matching.presentation.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.global.common.api.response.ErrorResponse;
import com.pairing.global.filter.TraceIdFilter;
import com.pairing.global.ratelimit.RateLimitProvider;
import com.pairing.matching.exception.MatchingErrorCode;
import com.pairing.matching.infrastructure.config.MatchingRateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

/** 재추천 엔드포인트에 붙는 레이트리밋. 공용 AI 예산을 이 엔드포인트가 독점하지 못하게 막는다. */
@Component
@RequiredArgsConstructor
public class MatchingRateLimitInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final RateLimitProvider rateLimitProvider;
    private final MatchingRateLimitConfig matchingRateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String identifier = extractIdentifier();
        Bucket bucket = rateLimitProvider.getBucket(matchingRateLimitConfig, identifier);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            writeRateLimitedResponse(response, waitSeconds);
            return false;
        }
        return true;
    }

    /** 로그인 계정 기준으로 제한한다. JWT subject가 곧 계정 ID다(CurrentAccountIdArgumentResolver와 동일 규칙). */
    private String extractIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "ACCOUNT_" + authentication.getName();
        }
        return "ANONYMOUS";
    }

    private void writeRateLimitedResponse(HttpServletResponse response, long waitSeconds) throws IOException {
        MatchingErrorCode errorCode = MatchingErrorCode.RATE_LIMITED;

        response.setStatus(errorCode.getStatus().value());
        response.setHeader("Retry-After", String.valueOf(waitSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = new ErrorResponse(Instant.now(), errorCode.getStatus().value(), errorCode.getCode(),
                errorCode.getMessage(), TraceIdFilter.currentTraceId());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
