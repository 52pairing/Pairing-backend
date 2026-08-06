package com.pairing.global.config;

import com.pairing.global.security.CurrentAccountIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentAccountIdArgumentResolver currentAccountIdArgumentResolver;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 주의: GlobalSecurityConfig의 CORS와 충돌하면
        // "다중 Access-Control-Allow-Origin 헤더" 에러가 발생하므로 비워둔다.
        // CORS 설정은 시큐리티 설정에서 전담한다.
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // @CurrentAccountId Long accountId 로 로그인 사용자를 받을 수 있게 한다.
        resolvers.add(currentAccountIdArgumentResolver);
    }
}
