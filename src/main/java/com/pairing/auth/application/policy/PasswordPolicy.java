package com.pairing.auth.application.policy;

import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 비밀번호 형식 규칙: 대문자 + 소문자 + 숫자 + 특수문자, 8자 이상 20자 이하. (요구사항 R13/R14)
 *
 * <p>프론트 검증만 두면 API를 직접 호출해 우회할 수 있으므로 서버에서도 같은 규칙을 적용한다.
 */
public final class PasswordPolicy {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,20}$");

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordPolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new BusinessException(AuthErrorCode.INVALID_PASSWORD_FORMAT);
        }
    }

    public static void validateConfirm(String rawPassword, String confirmPassword) {
        if (rawPassword == null || !rawPassword.equals(confirmPassword)) {
            throw new BusinessException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
    }

    /** 임시 비밀번호를 만든다. 각 문자 종류를 최소 1개씩 포함해 형식 규칙을 항상 만족시킨다. */
    public static String generateTemporary() {
        List<Character> characters = new ArrayList<>();
        characters.add(pick(UPPERCASE));
        characters.add(pick(LOWERCASE));
        characters.add(pick(DIGITS));
        characters.add(pick(SPECIALS));

        String all = UPPERCASE + LOWERCASE + DIGITS + SPECIALS;
        while (characters.size() < TEMP_PASSWORD_LENGTH) {
            characters.add(pick(all));
        }

        Collections.shuffle(characters, RANDOM);

        StringBuilder builder = new StringBuilder(characters.size());
        characters.forEach(builder::append);
        return builder.toString();
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
