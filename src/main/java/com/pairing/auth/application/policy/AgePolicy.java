package com.pairing.auth.application.policy;

import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;

import java.time.LocalDate;
import java.time.Period;

/**
 * 가입 연령 제한.
 *
 * <p>요구사항에는 "만 18세 미만 불가"와 "(2007년생 이후)"가 함께 적혀 있는데 두 기준이 서로 다르다.
 * 생일까지 반영하는 만 나이로 구현했다. 고정 연도 기준으로 바꿀 경우 이 클래스만 고치면 된다.
 */
public final class AgePolicy {

    private AgePolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static void validate(LocalDate birthDate, int minimumAge) {
        if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
            throw new BusinessException(AuthErrorCode.UNDER_MINIMUM_AGE);
        }

        int age = Period.between(birthDate, LocalDate.now()).getYears();
        if (age < minimumAge) {
            throw new BusinessException(AuthErrorCode.UNDER_MINIMUM_AGE);
        }
    }
}
