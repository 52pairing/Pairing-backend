package com.pairing.global.infrastructure.crypto;

import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.exception.RequiredPropertyMissingException;
import com.pairing.global.port.out.DataEncryptionPort;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 암복호화 어댑터.
 *
 * <p>저장 형식은 {@code IV(12바이트) || 암호문+태그} 이다. IV를 앞에 붙여야 키 교체 없이도
 * 매번 다른 IV를 쓸 수 있고, 같은 카드번호가 항상 같은 바이트로 저장되는 문제를 피한다.
 *
 * <p>키를 주입하지 않으면 기동을 실패시킨다. 기본 키를 두면 공개된 키로 카드번호가 암호화되고
 * 그 사실을 아무도 모르는 상태로 서비스가 돌아간다. (jwt.secret-key와 같은 이유)
 */
@Component
public class AesGcmDataEncryptionAdapter implements DataEncryptionPort {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final int KEY_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.crypto.data-key:}")
    private String base64Key;

    private SecretKey key;

    @PostConstruct
    protected void init() {
        if (base64Key == null || base64Key.isBlank()) {
            throw new RequiredPropertyMissingException(
                    "app.crypto.data-key",
                    "DATA_ENCRYPTION_KEY",
                    "카드번호/계좌번호 암호화 키가 비어 있습니다.",
                    """
                    32바이트 키를 Base64로 인코딩해 주입하세요.

                      1) 키 생성
                         openssl rand -base64 32

                      2) 주입 (Git Bash / macOS / Linux)
                         export DATA_ENCRYPTION_KEY=생성한값

                      3) IntelliJ에서 실행하는 경우
                         Run/Debug Configurations > Environment variables 에 DATA_ENCRYPTION_KEY 추가

                    키를 바꾸면 기존에 저장된 암호문을 복호화할 수 없습니다. 교체 시 재암호화 절차가 필요합니다.""");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new RequiredPropertyMissingException(
                    "app.crypto.data-key",
                    "DATA_ENCRYPTION_KEY",
                    "암호화 키가 Base64 형식이 아닙니다.",
                    "openssl rand -base64 32 로 생성한 값을 그대로 주입하세요.");
        }

        if (keyBytes.length != KEY_LENGTH) {
            throw new RequiredPropertyMissingException(
                    "app.crypto.data-key",
                    "DATA_ENCRYPTION_KEY",
                    "암호화 키 길이가 올바르지 않습니다. (현재 %d바이트, 필요 %d바이트)"
                            .formatted(keyBytes.length, KEY_LENGTH),
                    "AES-256을 사용하므로 정확히 32바이트여야 합니다. openssl rand -base64 32");
        }

        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public byte[] encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (Exception e) {
            // 원인에 평문이 섞일 수 있어 메시지를 그대로 올리지 않는다. 상세는 로그/traceId로 추적한다.
            throw new BusinessException(GlobalErrorCode.SERVER_ERROR);
        }
    }

    @Override
    public String decrypt(byte[] cipherText) {
        if (cipherText == null) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(GlobalErrorCode.SERVER_ERROR);
        }
    }
}
