package com.pairing.auth.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 인증 기준값. 기본값은 요구사항 R13/R14에서 온다.
 *
 * <p>application.yaml의 app.auth.* 로 덮어쓸 수 있다. 코드와 yaml 중 하나만 바꾸면
 * 환경마다 동작이 갈리므로 두 곳을 함께 수정한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthSettings {

    /** 인증 메일 발신자 주소. */
    private String fromAddress = "no-reply@pairing.local";

    /** 비밀번호 재설정 링크를 조립할 프론트엔드 주소. */
    private String frontBaseUrl = "http://localhost:17000";

    /** 인증코드 유효시간. */
    private Duration emailCodeTtl = Duration.ofMinutes(3);

    /** 인증코드 입력 시도 상한. 넘으면 코드를 폐기한다. */
    private int emailCodeMaxAttempt = 5;

    /** 이메일 발송 상한(집계 구간 기준). */
    private int emailSendLimit = 15;

    /** 이메일 발송 상한 집계 구간. */
    private Duration emailSendWindow = Duration.ofHours(1);

    /** 인증 완료 후 가입 제출까지 유효한 시간. */
    private Duration verifiedMarkerTtl = Duration.ofMinutes(30);

    /** 비밀번호 연속 실패 잠금 기준. */
    private int loginFailMax = 5;

    /** IP 기준 로그인 실패 상한. */
    private int ipFailMax = 20;

    /** IP 실패 집계 구간. */
    private Duration ipFailWindow = Duration.ofHours(1);

    /** IP 차단 유지 시간. */
    private Duration ipBlockDuration = Duration.ofHours(2);

    /** 비밀번호 재설정 링크 유효시간. */
    private Duration passwordResetTtl = Duration.ofMinutes(3);

    /** 소셜 가입 티켓 유효시간. */
    private Duration signupTicketTtl = Duration.ofMinutes(30);

    /** 소셜 인가 state 유효시간. */
    private Duration oauthStateTtl = Duration.ofMinutes(5);

    /** 탈퇴 후 재가입 제한 일수. */
    private int rejoinRestrictionDays = 30;

    /** 가입 가능 최소 연령(만). */
    private int minimumAge = 18;
}
