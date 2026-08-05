package com.pairing.auth.application.policy;

/**
 * 아이디 찾기 응답용 마스킹.
 *
 * <p>규칙(요구사항 R14): 로컬파트 앞 2글자만 공개하고 나머지는 가린다. 도메인은 그대로 보여준다.
 * 로컬파트가 2글자 이하면 첫 글자만 공개한다.
 */
public final class EmailMaskingPolicy {

    private static final int VISIBLE_LENGTH = 2;
    private static final char MASK = '*';

    private EmailMaskingPolicy() {
        throw new IllegalStateException("Utility class");
    }

    public static String mask(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        int visible = localPart.length() <= VISIBLE_LENGTH ? 1 : VISIBLE_LENGTH;
        if (localPart.length() <= visible) {
            return localPart + domain;
        }

        return localPart.substring(0, visible)
                + String.valueOf(MASK).repeat(localPart.length() - visible)
                + domain;
    }
}
