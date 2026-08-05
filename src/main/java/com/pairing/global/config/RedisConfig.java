package com.pairing.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 수동 사용(RedisTemplate)을 위한 설정.
 *
 * <p>키/값 직렬화 방식을 명시해두면 redis-cli로 값을 확인할 때 사람이 읽을 수 있는 형태로 저장된다.
 * (기본 JdkSerializationRedisSerializer는 바이너리로 저장되어 디버깅이 어렵다)
 */
@Configuration
public class RedisConfig {

    /** 역직렬화를 허용할 애플리케이션 패키지. 여기 없는 타입은 @class에 뭐가 적혀 있어도 복원하지 않는다. */
    private static final String APPLICATION_BASE_PACKAGE = "com.pairing.";

    /** 문자열 전용 템플릿. 리프레시 토큰, 인증코드, 플래그 등 단순 값 저장에 사용한다. */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    /**
     * 객체를 JSON으로 저장하는 템플릿. DTO 캐싱 등에 사용한다.
     *
     * <p>값에 {@code @class}를 함께 저장해야 꺼낼 때 원래 타입으로 복원된다.
     * 타입 정보가 없으면 {@code LinkedHashMap}으로 돌아와 캐스팅 지점에서 터진다.
     */
    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // defaultTyping(true): 타입 정보를 어디에 붙일지는 Spring Data Redis의 판단에 맡긴다.
        // 직접 activateDefaultTyping(EVERYTHING)을 쓰면 Long 같은 래퍼 타입까지 감싸버려서
        // 저장 형태가 지저분해지고 허용 목록도 그만큼 넓혀야 한다.
        GenericJackson2JsonRedisSerializer valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(redisObjectMapper())
                .defaultTyping(true)
                .build();

        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }

    /**
     * Redis 전용 ObjectMapper.
     *
     * <p>웹 ObjectMapper를 재사용하지 않는 이유가 두 가지다.
     * <ol>
     *   <li>웹 쪽에는 {@code CdnUrlSerializerModifier}가 붙어 있어, 그대로 쓰면 캐시에
     *       object key가 아니라 CDN 절대 URL이 저장된다. CDN 주소를 바꾸면 캐시가 전부 무효가 된다.</li>
     *   <li>캐시는 타입 복원을 위해 default typing이 필요한데, 이걸 웹 응답에 켜면
     *       모든 API 응답에 {@code @class} 필드가 노출된다.</li>
     * </ol>
     */
    private ObjectMapper redisObjectMapper() {
        // default typing은 임의 클래스의 역직렬화 통로가 되므로 허용 대상을 좁힌다.
        // (Spring Data Redis 기본값은 LaissezFaireSubTypeValidator로 사실상 무제한이다)
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(APPLICATION_BASE_PACKAGE)
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();

        return JsonMapper.builder()
                .addModule(new JavaTimeModule())        // Instant, LocalDateTime 등
                .addModule(new ParameterNamesModule())  // record/생성자 파라미터 바인딩
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601로 저장
                .polymorphicTypeValidator(typeValidator)
                .build();
    }
}
