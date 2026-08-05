package com.pairing.auth.application.policy;

import java.security.SecureRandom;

/** 6자리 숫자 인증코드. 예측 가능한 Random 대신 SecureRandom을 쓴다. */
public final class VerificationCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int BOUND = 1_000_000;

    private VerificationCodeGenerator() {
        throw new IllegalStateException("Utility class");
    }

    public static String generate() {
        return String.format("%06d", RANDOM.nextInt(BOUND));
    }
}
