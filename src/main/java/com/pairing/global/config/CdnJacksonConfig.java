package com.pairing.global.config;

import com.pairing.global.infrastructure.s3.CdnUrlSerializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 웹(HTTP 응답) ObjectMapper에만 CDN URL 매핑 모디파이어를 등록한다.
 *
 * <p>Redis 캐시용 ObjectMapper에는 등록하지 않으므로 캐시에는 상대경로(key)가 그대로 저장되어
 * CDN 주소 변경에 영향을 받지 않고, HTTP 응답 직렬화 시점에만 절대 URL로 변환된다.
 */
@Configuration
public class CdnJacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer cdnUrlObjectMapperCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("CdnUrlModule");
            module.setSerializerModifier(new CdnUrlSerializerModifier());
            builder.postConfigurer(objectMapper -> objectMapper.registerModule(module));
        };
    }
}
