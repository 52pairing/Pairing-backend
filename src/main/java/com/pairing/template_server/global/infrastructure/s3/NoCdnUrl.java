package com.pairing.template_server.global.infrastructure.s3;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link CdnMappable} DTO에서 이름 규칙({@code ~Url}/{@code ~Urls})에는 걸리지만
 * CDN 변환에서 제외해야 하는 필드에 붙인다. (예: 외부 링크 URL, 내부 API 경로)
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoCdnUrl {
}
