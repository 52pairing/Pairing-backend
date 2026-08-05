package com.pairing.terms.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** terms_agreement 테이블 매핑. (account_id, terms_id) 유니크. */
@Entity
@Table(name = "terms_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "terms_id", nullable = false)
    private Long termsId;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public TermsAgreementJpaEntity(Long id, Long accountId, Long termsId, boolean agreed, LocalDateTime agreedAt,
                                   String userAgent) {
        this.id = id;
        this.accountId = accountId;
        this.termsId = termsId;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
        this.userAgent = userAgent;
    }
}
