package com.pairing.template_server.global.config;

import com.pairing.template_server.example.presentation.api.response.ExampleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * objectRedisTemplate이 값을 원래 타입으로 복원하는지 검증한다.
 *
 * <p>커스텀 ObjectMapper를 GenericJackson2JsonRedisSerializer에 넘길 때 default typing을 켜지 않으면
 * @class가 붙지 않아 LinkedHashMap으로 복원되고, 캐시를 읽는 지점에서 ClassCastException이 난다.
 * 실제 Redis 연결 없이 직렬화기만 확인하므로 이 테스트는 Redis가 꺼져 있어도 통과한다.
 */
@SpringBootTest
class RedisSerializationTest {

    @Autowired
    @Qualifier("objectRedisTemplate")
    RedisTemplate<String, Object> objectRedisTemplate;

    record Cached(Long id, String name, Instant createdAt, List<String> tags) {}

    @Test
    @DisplayName("record를 저장하고 꺼내면 원래 타입과 값이 그대로 복원된다")
    void roundTripKeepsConcreteType() {
        RedisSerializer<?> serializer = objectRedisTemplate.getValueSerializer();
        Cached original = new Cached(1L, "예시", Instant.parse("2026-05-21T07:09:00Z"), List.of("a", "b"));

        @SuppressWarnings("unchecked")
        byte[] bytes = ((RedisSerializer<Object>) serializer).serialize(original);

        assertThat(new String(bytes)).contains("@class");
        assertThat(serializer.deserialize(bytes))
                .isInstanceOf(Cached.class)
                .isEqualTo(original);
    }

    @Test
    @DisplayName("캐시에는 CDN 절대 URL이 아니라 object key가 저장된다")
    void doesNotApplyCdnMappingToCache() {
        RedisSerializer<?> serializer = objectRedisTemplate.getValueSerializer();

        @SuppressWarnings("unchecked")
        byte[] bytes = ((RedisSerializer<Object>) serializer)
                .serialize(new ExampleResponse(1L, "examples/uuid.png"));

        // 웹 ObjectMapper를 재사용하면 여기에 http://... 가 저장되고 CDN 주소 변경 시 캐시가 깨진다.
        assertThat(new String(bytes)).contains("examples/uuid.png").doesNotContain("http");
    }
}
