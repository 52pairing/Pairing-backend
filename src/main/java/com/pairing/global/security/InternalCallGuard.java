package com.pairing.global.security;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 다른 서버(관리자 서버 등)가 이 서버를 부를 때 쓰는 인증.
 *
 * <p>서버끼리의 호출이라 JWT 를 쓸 수 없다. 사용자의 토큰이 아니라 <b>서비스의 신원</b>을
 * 증명해야 하는데, 관리자 서버는 세션 쿠키 기반이라 이 서버가 검증할 수 있는 토큰이 없다.
 *
 * <p>{@link OpsApiKeyGuard} 와 키를 나눈다. 저쪽은 사람이 Swagger 에서 누르는 운영 버튼이고
 * 이쪽은 서버가 자동으로 부르는 통로다. 한 키를 같이 쓰면 사람에게 알려준 키로 서버 API 까지
 * 열리고, 키를 돌릴 때 둘 중 하나가 반드시 끊긴다.
 */
@Slf4j
@Component
public class InternalCallGuard {

    public static final String HEADER = "X-Internal-Api-Key";

    private final String configuredKey;

    public InternalCallGuard(@Value("${internal.api-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    /** 키가 다르면 {@code GLOBAL_005}(403). */
    public void verify(String presentedKey) {
        // 키를 안 넣었으면 잠근다. 미설정을 "제한 없음"으로 읽으면 환경변수 한 줄 빠뜨린 순간
        // 아무나 남의 계정으로 알림을 만들 수 있게 된다.
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("[내부 호출] INTERNAL_API_KEY 가 설정되지 않아 요청을 거부했습니다.");
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        if (presentedKey == null || !constantTimeEquals(configuredKey, presentedKey)) {
            log.warn("[내부 호출] 내부 호출 키가 일치하지 않아 요청을 거부했습니다.");
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 앞에서부터 한 글자씩 비교하다 다르면 바로 멈추는 {@code equals} 는 응답 시간에 정답의
     * 앞부분이 묻어난다. {@code MessageDigest.isEqual} 은 길이가 같으면 끝까지 비교한다.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
