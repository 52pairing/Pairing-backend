package com.pairing.auth.application.policy;

import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호 규칙: 대문자 + 소문자 + 숫자 + 특수문자, 8~20자.
 *
 * <p>프론트 검증은 우회할 수 있으므로 이 규칙이 서버에서 반드시 지켜져야 한다.
 */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Passw0rd!",
            "Passw0r!",              // 8자 하한 경계
            "Abcdefg1!@#Abcdefg1!"   // 20자 상한 경계
    })
    @DisplayName("네 종류를 모두 포함한 8~20자 비밀번호는 통과한다")
    void acceptsValidPassword(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Pw0rd!",              // 7자
            "Abcdefg1!@#Abcdefg1!x", // 21자
            "passw0rd!",           // 대문자 없음
            "PASSW0RD!",           // 소문자 없음
            "Password!",           // 숫자 없음
            "Passw0rdd",           // 특수문자 없음
            "Passw0rd !"           // 공백 포함
    })
    @DisplayName("형식에 맞지 않으면 AU_010으로 거부한다")
    void rejectsInvalidPassword(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_PASSWORD_FORMAT);
    }

    @Test
    @DisplayName("null 비밀번호도 거부한다")
    void rejectsNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 AU_011로 거부한다")
    void rejectsMismatchedConfirm() {
        assertThatThrownBy(() -> PasswordPolicy.validateConfirm("Passw0rd!", "Passw0rd?"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
    }

    @Test
    @DisplayName("생성한 임시 비밀번호는 항상 형식 규칙을 만족한다")
    void generatedTemporaryPasswordAlwaysValid() {
        // 문자 종류를 무작위로 뽑으므로 한 번만 확인하면 우연히 통과할 수 있다.
        for (int i = 0; i < 200; i++) {
            String temporary = PasswordPolicy.generateTemporary();
            assertThatCode(() -> PasswordPolicy.validate(temporary))
                    .as("생성된 임시 비밀번호: %s", temporary)
                    .doesNotThrowAnyException();
        }
    }
}
