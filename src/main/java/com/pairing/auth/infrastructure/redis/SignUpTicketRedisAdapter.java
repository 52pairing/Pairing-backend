package com.pairing.auth.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.auth.application.port.SignUpTicket;
import com.pairing.auth.application.port.SignUpTicketPort;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.util.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 소셜 가입 티켓 저장소.
 *
 * <p>값을 JSON 문자열로 직접 다룬다. objectRedisTemplate(@class 포함)을 쓰면 타입 정보가 값에 섞여
 * 클래스 위치를 바꿀 때 기존 티켓을 못 읽는다. 티켓은 30분짜리 임시 데이터라 단순 JSON이 낫다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignUpTicketRedisAdapter implements SignUpTicketPort {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(String ticket, SignUpTicket data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key(ticket), objectMapper.writeValueAsString(data), ttl);
        } catch (JsonProcessingException e) {
            log.error("가입 티켓 직렬화 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    @Override
    public Optional<SignUpTicket> find(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return Optional.empty();
        }

        String value = redisTemplate.opsForValue().get(key(ticket));
        if (value == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(value, SignUpTicket.class));
        } catch (JsonProcessingException e) {
            log.error("가입 티켓 역직렬화 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String ticket) {
        redisTemplate.delete(key(ticket));
    }

    private String key(String ticket) {
        return RedisKeys.SIGNUP_TICKET_PREFIX + ticket;
    }
}
