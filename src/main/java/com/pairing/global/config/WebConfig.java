package com.pairing.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 주의: GlobalSecurityConfig의 CORS와 충돌하면
        // "다중 Access-Control-Allow-Origin 헤더" 에러가 발생하므로 비워둔다.
        // CORS 설정은 시큐리티 설정에서 전담한다.
    }
}
