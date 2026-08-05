package com.pairing.auth.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 이메일 인증코드.
 *
 * <p>코드 평문은 저장하지 않는다. DB가 유출돼도 인증을 통과할 수 없어야 한다.
 * 시도 횟수를 함께 들고 있어 무차별 대입으로 6자리를 맞추는 것을 막는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    private Long id;
    private String email;
    private VerificationPurpose purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime verifiedAt;
    private int attemptCount;

    private EmailVerification(Long id, String email, VerificationPurpose purpose, String codeHash,
                              LocalDateTime expiresAt, LocalDateTime verifiedAt, int attemptCount) {
        this.id = id;
        this.email = email;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.verifiedAt = verifiedAt;
        this.attemptCount = attemptCount;
    }

    public static EmailVerification issue(String email, VerificationPurpose purpose, String codeHash,
                                          Duration ttl) {
        return new EmailVerification(null, email, purpose, codeHash,
                LocalDateTime.now().plus(ttl), null, 0);
    }

    public static EmailVerification reconstitute(Long id, String email, VerificationPurpose purpose,
                                                 String codeHash, LocalDateTime expiresAt,
                                                 LocalDateTime verifiedAt, int attemptCount) {
        return new EmailVerification(id, email, purpose, codeHash, expiresAt, verifiedAt, attemptCount);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }

    public boolean isVerified() {
        return this.verifiedAt != null;
    }

    public boolean isAttemptExceeded(int maxAttempt) {
        return this.attemptCount >= maxAttempt;
    }

    public void increaseAttempt() {
        this.attemptCount++;
    }

    public void markVerified() {
        this.verifiedAt = LocalDateTime.now();
    }

    /** 시도 횟수를 소진시켜 코드를 즉시 폐기한다. */
    public void discard(int maxAttempt) {
        this.attemptCount = maxAttempt;
    }
}
