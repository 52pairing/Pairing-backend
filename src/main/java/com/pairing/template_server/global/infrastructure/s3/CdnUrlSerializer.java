package com.pairing.template_server.global.infrastructure.s3;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Collection;

/**
 * DB에 저장된 object key(상대경로)를 응답 직렬화 시점에 절대 URL로 변환하는 Jackson 직렬화기.
 *
 * <p>String 필드와 {@code List<String>} 필드 모두 처리한다.
 * 값이 비어 있거나 이미 {@code http}로 시작하면 그대로 두고, 그 외에는 {@code cdnBase + "/" + key} 형태로 만든다.
 *
 * <p>{@link CdnUrlSerializerModifier}가 {@link CdnMappable} DTO의 URL 필드에 이 직렬화기를 할당한다.
 * CDN 루트는 스프링 초기화 시 {@link CdnUrlConfigurer}가 주입하는 static 값을 사용한다.
 */
public class CdnUrlSerializer extends JsonSerializer<Object> {

    private static volatile String cdnBase = "";

    static void configure(String base) {
        cdnBase = base == null ? "" : base;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value instanceof String s) {
            gen.writeString(resolve(s));
            return;
        }
        if (value instanceof Collection<?> collection) {
            gen.writeStartArray();
            for (Object element : collection) {
                if (element instanceof String s) {
                    gen.writeString(resolve(s));
                } else if (element == null) {
                    gen.writeNull();
                } else {
                    gen.writeObject(element);
                }
            }
            gen.writeEndArray();
            return;
        }
        gen.writeObject(value);
    }

    private String resolve(String value) {
        if (value == null || value.isBlank()
                || value.startsWith("http://") || value.startsWith("https://")) {
            // 빈 값 또는 이미 절대 URL이면 그대로 반환 (구 데이터/외부 URL 방어)
            return value;
        }
        String key = value.startsWith("/") ? value.substring(1) : value;
        return cdnBase.isBlank() ? key : cdnBase + "/" + key;
    }
}
