package com.pairing.global.aop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그에 평문 비밀번호·카드번호가 남지 않는지 확인한다.
 *
 * <p>Command/Request가 record라 toString()에 모든 필드가 그대로 찍힌다.
 * 필드를 추가하다가 다시 새는 일이 없도록 회귀 테스트로 남긴다.
 */
class ApiLoggingAopTest {

    @Test
    @DisplayName("비밀번호와 카드번호는 마스킹된다")
    void masksCredentials() {
        Object[] args = {
                "FreelancerSignUpCommand[email=user@pairing.com, password=Test1234!, "
                        + "passwordConfirm=Test1234!, cardNumber=1234567812345678, "
                        + "accountNo=11012345678901, accountHolder=홍길동]"
        };

        String masked = ApiLoggingAop.maskSensitive(args);

        assertThat(masked)
                .doesNotContain("Test1234!")
                .doesNotContain("1234567812345678")
                .doesNotContain("11012345678901")
                .contains("password=***")
                .contains("cardNumber=***")
                // 민감하지 않은 값은 그대로 남아야 추적에 쓸 수 있다.
                .contains("email=user@pairing.com")
                .contains("accountHolder=홍길동");
    }

    @Test
    @DisplayName("인증코드와 토큰도 마스킹된다")
    void masksCodesAndTokens() {
        Object[] args = {
                "ConfirmCodeCommand[email=user@pairing.com, purpose=SIGNUP, code=123456]",
                "SocialSignUpCommand[signUpTicket=8f1c-abcd, name=홍길동]"
        };

        String masked = ApiLoggingAop.maskSensitive(args);

        assertThat(masked).doesNotContain("123456").doesNotContain("8f1c-abcd");
    }

    @Test
    @DisplayName("인자가 없거나 null이어도 예외를 던지지 않는다")
    void handlesEmptyArgs() {
        assertThat(ApiLoggingAop.maskSensitive(null)).isEqualTo("[]");
        assertThat(ApiLoggingAop.maskSensitive(new Object[0])).isEqualTo("[]");
    }
}
