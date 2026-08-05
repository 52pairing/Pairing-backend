package com.pairing.terms.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 약관 동의 이력. 분쟁 시 증거로 쓰이므로 동의 시각과 User-Agent를 함께 남긴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    private static final int USER_AGENT_MAX_LENGTH = 500;

    private Long id;
    private Long accountId;
    private Long termsId;
    private boolean agreed;
    private LocalDateTime agreedAt;
    private String userAgent;

    private TermsAgreement(Long id, Long accountId, Long termsId, boolean agreed, LocalDateTime agreedAt,
                           String userAgent) {
        this.id = id;
        this.accountId = accountId;
        this.termsId = termsId;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.userAgent = userAgent;
    }

    public static TermsAgreement create(Long accountId, Long termsId, boolean agreed, String userAgent) {
        return new TermsAgreement(null, accountId, termsId, agreed, LocalDateTime.now(), truncate(userAgent));
    }

    public static TermsAgreement reconstitute(Long id, Long accountId, Long termsId, boolean agreed,
                                              LocalDateTime agreedAt, String userAgent) {
        return new TermsAgreement(id, accountId, termsId, agreed, agreedAt, userAgent);
    }

    // User-Agent는 클라이언트가 보낸 값이라 길이 제한이 없다. 컬럼 길이를 넘으면 INSERT가 실패한다.
    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }
}
