package com.pairing.matching.presentation.config;

import com.pairing.matching.presentation.interceptor.MatchingRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class MatchingWebConfig implements WebMvcConfigurer {

    private final MatchingRateLimitInterceptor matchingRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 재추천은 Pairing-python(Gemini)을 다시 호출하므로 이 경로만 제한한다.
        registry.addInterceptor(matchingRateLimitInterceptor)
                .addPathPatterns("/api/v1/matchings/positions/*/rerecommendations");
    }
}
