package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** social_account 테이블 매핑. (provider, provider_uid) 유니크. */
@Entity
@Table(name = "social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_uid", nullable = false, length = 255)
    private String providerUid;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    public SocialAccountJpaEntity(Long id, Long accountId, SocialProvider provider, String providerUid,
                                  String providerEmail, boolean emailVerified, LocalDateTime connectedAt) {
        this.id = id;
        this.accountId = accountId;
        this.provider = provider;
        this.providerUid = providerUid;
        this.providerEmail = providerEmail;
        this.emailVerified = emailVerified;
        this.connectedAt = connectedAt;
    }
}
