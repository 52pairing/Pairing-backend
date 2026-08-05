package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
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

/**
 * account 테이블 매핑.
 *
 * <p>created_at / updated_at 은 매핑하지 않는다. DB 기본값과 트리거(set_updated_at)가 채우므로
 * 애플리케이션이 값을 들고 있으면 두 곳에서 시각을 관리하게 된다.
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "signup_type", nullable = false, length = 20)
    private SignupType signupType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "is_temp_password", nullable = false)
    private boolean tempPassword;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "suspend_reason", length = 500)
    private String suspendReason;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdraw_reason", length = 500)
    private String withdrawReason;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "phone_hash", length = 64)
    private String phoneHash;

    @Column(name = "rejoin_available_at")
    private LocalDateTime rejoinAvailableAt;

    @Column(name = "purge_at")
    private LocalDateTime purgeAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public AccountJpaEntity(Long id, String email, String passwordHash, Role role, String name, String phone,
                            SignupType signupType, AccountStatus status, boolean emailVerified, int loginFailCount,
                            LocalDateTime lockedAt, boolean tempPassword, LocalDateTime passwordUpdatedAt,
                            LocalDateTime lastLoginAt, String suspendReason, LocalDateTime withdrawnAt,
                            String withdrawReason, String emailHash, String phoneHash,
                            LocalDateTime rejoinAvailableAt, LocalDateTime purgeAt, LocalDateTime deletedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.name = name;
        this.phone = phone;
        this.signupType = signupType;
        this.status = status;
        this.emailVerified = emailVerified;
        this.loginFailCount = loginFailCount;
        this.lockedAt = lockedAt;
        this.tempPassword = tempPassword;
        this.passwordUpdatedAt = passwordUpdatedAt;
        this.lastLoginAt = lastLoginAt;
        this.suspendReason = suspendReason;
        this.withdrawnAt = withdrawnAt;
        this.withdrawReason = withdrawReason;
        this.emailHash = emailHash;
        this.phoneHash = phoneHash;
        this.rejoinAvailableAt = rejoinAvailableAt;
        this.purgeAt = purgeAt;
        this.deletedAt = deletedAt;
    }
}
