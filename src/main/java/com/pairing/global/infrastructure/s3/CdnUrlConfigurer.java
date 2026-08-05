package com.pairing.global.infrastructure.s3;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 {@link CdnUrlSerializer}의 CDN 루트를 주입한다.
 * (직렬화기는 Jackson이 리플렉션으로 생성하므로 스프링 빈 주입 대신 static 값을 사용)
 */
@Component
@RequiredArgsConstructor
public class CdnUrlConfigurer {

    private final S3Settings s3Settings;

    @PostConstruct
    void init() {
        CdnUrlSerializer.configure(s3Settings.getCdnBase());
    }
}
