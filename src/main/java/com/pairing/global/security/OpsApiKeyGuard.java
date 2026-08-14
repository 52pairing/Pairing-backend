package com.pairing.global.security;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 운영자가 손으로 누르는 API 를 여는 열쇠 검증기.
 *
 * <p>관리자 로그인을 쓰지 않는다. 관리자 계정은 관리자 서버의 {@code admin_user} 에만 있고 그쪽은
 * 세션 쿠키, 이 서버는 JWT 다. 이 서버에는 {@code Role.ADMIN} 을 가질 수 있는 계정을 만드는 경로가
 * 아예 없어서 {@code hasRole('ADMIN')} 은 아무도 통과하지 못하는 문이 된다.
 *
 * <p>로그인을 대체하는 것이 아니라 <b>덧붙이는</b> 것이다. 이 열쇠를 요구하는 API 도 로그인은 그대로
 * 필요하다. 열쇠 하나가 새더라도 로그인 없이는 부를 수 없다.
 */
@Slf4j
@Component
public class OpsApiKeyGuard {

    private final String configuredKey;

    // application.yaml 에 항목을 만들지 않고 환경변수를 직접 읽는다. 스프링은 OS 환경변수도
    // 프로퍼티로 취급하므로 이대로 동작한다. 설정 파일을 건드리지 않아 배포 파일이 그대로다.
    public OpsApiKeyGuard(@Value("${OPS_API_KEY:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    /** 열쇠가 다르면 {@code GLOBAL_005}(403). */
    public void verify(String presentedKey) {
        // 키를 안 넣었으면 잠근다. 미설정을 "제한 없음"으로 읽으면 환경변수 하나 빠뜨린 순간
        // 운영 API 가 열린 채로 돌아간다.
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("[운영 API] OPS_API_KEY 가 설정되지 않아 요청을 거부했습니다.");
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
        if (presentedKey == null || !constantTimeEquals(configuredKey, presentedKey)) {
            log.warn("[운영 API] 운영 키가 일치하지 않아 요청을 거부했습니다.");
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }
    }

    /**
     * 앞에서부터 한 글자씩 비교하다 다르면 바로 멈추는 {@code equals} 는 응답 시간에 정답의 앞부분이
     * 묻어난다. {@code MessageDigest.isEqual} 은 길이가 같으면 끝까지 비교한다.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
