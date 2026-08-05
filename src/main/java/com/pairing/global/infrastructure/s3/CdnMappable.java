package com.pairing.global.infrastructure.s3;

/**
 * 응답 DTO가 구현하면, 반환(직렬화) 시점에 이름이 {@code ~Url} / {@code ~Urls}로 끝나는
 * String(또는 List&lt;String&gt;) 필드를 자동으로 CDN 절대 URL로 변환한다.
 *
 * <p>DB에는 object key(상대경로)만 저장하고, 이 인터페이스를 구현한 DTO를 반환하면
 * {@link CdnUrlSerializerModifier}가 해당 필드에 {@link CdnUrlSerializer}를 적용한다.
 *
 * <p>필드별로 애노테이션을 붙일 필요 없이 {@code implements CdnMappable} 한 줄이면 된다.
 * URL 이름 규칙에 걸리지만 변환하면 안 되는 필드(외부 링크·API 경로 등)는 {@link NoCdnUrl}로 제외한다.
 */
public interface CdnMappable {
}
