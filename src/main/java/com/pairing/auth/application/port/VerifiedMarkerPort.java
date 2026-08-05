package com.pairing.auth.application.port;

import com.pairing.auth.domain.model.VerificationPurpose;

import java.time.Duration;

/**
 * "이 이메일은 방금 인증을 마쳤다"는 마커.
 *
 * <p>가입 폼 제출은 인증 확인보다 늦게 온다. 인증코드 자체는 3분이면 사라지므로,
 * 인증 완료 사실만 따로 짧게 보관해 제출 시점에 확인한다.
 */
public interface VerifiedMarkerPort {

    void mark(String email, VerificationPurpose purpose, Duration ttl);

    boolean isVerified(String email, VerificationPurpose purpose);

    void clear(String email, VerificationPurpose purpose);
}
