package com.pairing.auth.application.policy;

import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 만 18세 미만 가입 불가. 생일까지 반영한다. */
class AgePolicyTest {

    private static final int MINIMUM_AGE = 18;

    @Test
    @DisplayName("만 18세 생일 당일은 가입할 수 있다")
    void allowsExactlyMinimumAge() {
        LocalDate birthDate = LocalDate.now().minusYears(MINIMUM_AGE);

        assertThatCode(() -> AgePolicy.validate(birthDate, MINIMUM_AGE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("생일 하루 전이면 아직 만 17세라 가입할 수 없다")
    void rejectsOneDayBeforeBirthday() {
        LocalDate birthDate = LocalDate.now().minusYears(MINIMUM_AGE).plusDays(1);

        assertThatThrownBy(() -> AgePolicy.validate(birthDate, MINIMUM_AGE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNDER_MINIMUM_AGE);
    }

    @Test
    @DisplayName("미래 날짜와 null은 거부한다")
    void rejectsFutureAndNull() {
        assertThatThrownBy(() -> AgePolicy.validate(LocalDate.now().plusDays(1), MINIMUM_AGE))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AgePolicy.validate(null, MINIMUM_AGE))
                .isInstanceOf(BusinessException.class);
    }
}
