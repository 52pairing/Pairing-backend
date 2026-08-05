package com.pairing.auth.application.policy;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 이메일/전화번호 정규화와 해시.
 *
 * <p>정규화를 빠뜨리면 "A@x.com"과 "a@x.com"이 서로 다른 계정으로 가입된다.
 * 유니크 제약은 대소문자와 하이픈을 구분하기 때문이다.
 */
public final class ContactPolicy {

    private ContactPolicy() {
        throw new IllegalStateException("Utility class");
    }

    /** 앞뒤 공백 제거 + 소문자. 저장과 조회 모두 이 값을 쓴다. */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /** 숫자만 남긴다. 화면의 하이픈은 프론트가 붙인다. */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        return phone.replaceAll("[^0-9]", "");
    }

    /** 탈퇴 이력 대조용 SHA-256. 원본을 남기지 않고도 재가입 제한을 판정할 수 있다. */
    public static String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK 표준이라 실제로는 발생하지 않는다.
            throw new BusinessException(GlobalErrorCode.SERVER_ERROR);
        }
    }
}
