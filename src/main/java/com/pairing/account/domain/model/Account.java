package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 통합 계정. 인증·정지·탈퇴의 단일 소스다.
 *
 * <p>클라이언트/프리랜서/관리자를 한 테이블에서 다루고, 역할별 상세는 각각의 프로필이 갖는다.
 * 이메일과 휴대폰번호는 스키마에서 전역 유니크라 역할이 달라도 같은 값으로 두 계정을 만들 수 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    private Long id;
    private String email;
    private String passwordHash;
    private Role role;
    private String name;
    private String phone;
    private SignupType signupType;
    private AccountStatus status;
    private boolean emailVerified;
    private int loginFailCount;
    private LocalDateTime lockedAt;
    private boolean tempPassword;
    private LocalDateTime passwordUpdatedAt;
    private LocalDateTime lastLoginAt;
    private String suspendReason;
    private LocalDateTime withdrawnAt;
    private String withdrawReason;
    private String emailHash;
    private String phoneHash;
    private LocalDateTime rejoinAvailableAt;
    private LocalDateTime purgeAt;
    private LocalDateTime deletedAt;

    private Account(String email, String passwordHash, Role role, String name, String phone,
                    SignupType signupType, boolean emailVerified) {
        validateRequired(email, role, name, phone);
        validatePasswordPresence(passwordHash, signupType);

        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.name = name;
        this.phone = phone;
        this.signupType = signupType;
        this.emailVerified = emailVerified;
        // 가입 완료 조건(필수 입력 + 이메일 인증 + 필수 약관)을 서비스가 모두 확인한 뒤 생성되므로 바로 ACTIVE다.
        this.status = AccountStatus.ACTIVE;
        this.loginFailCount = 0;
        this.tempPassword = false;
        this.passwordUpdatedAt = passwordHash == null ? null : LocalDateTime.now();
    }

    private Account(Long id, String email, String passwordHash, Role role, String name, String phone,
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

    /** 이메일 가입 계정. 이메일 인증을 마친 뒤에만 생성된다. */
    public static Account createByEmail(String email, String passwordHash, Role role, String name, String phone) {
        return new Account(email, passwordHash, role, name, phone, SignupType.EMAIL, true);
    }

    /** 소셜 가입 계정. 비밀번호가 없고, 이메일 인증 여부는 공급자가 알려준 값을 그대로 쓴다. */
    public static Account createBySocial(String email, Role role, String name, String phone, boolean emailVerified) {
        if (role != Role.FREELANCER) {
            throw new BusinessException(AccountErrorCode.SOCIAL_NOT_ALLOWED_FOR_ROLE);
        }
        return new Account(email, null, role, name, phone, SignupType.SOCIAL, emailVerified);
    }

    public static Account reconstitute(Long id, String email, String passwordHash, Role role, String name,
                                       String phone, SignupType signupType, AccountStatus status,
                                       boolean emailVerified, int loginFailCount, LocalDateTime lockedAt,
                                       boolean tempPassword, LocalDateTime passwordUpdatedAt,
                                       LocalDateTime lastLoginAt, String suspendReason, LocalDateTime withdrawnAt,
                                       String withdrawReason, String emailHash, String phoneHash,
                                       LocalDateTime rejoinAvailableAt, LocalDateTime purgeAt,
                                       LocalDateTime deletedAt) {
        return new Account(id, email, passwordHash, role, name, phone, signupType, status, emailVerified,
                loginFailCount, lockedAt, tempPassword, passwordUpdatedAt, lastLoginAt, suspendReason,
                withdrawnAt, withdrawReason, emailHash, phoneHash, rejoinAvailableAt, purgeAt, deletedAt);
    }

    // ==========================================
    // 로그인 관련 상태 전이
    // ==========================================

    /** 로그인 실패를 기록하고, 임계치에 도달하면 계정을 잠근다. */
    public void recordLoginFailure(int lockThreshold) {
        this.loginFailCount++;
        if (this.loginFailCount >= lockThreshold && this.status == AccountStatus.ACTIVE) {
            this.status = AccountStatus.LOCKED;
            this.lockedAt = LocalDateTime.now();
        }
    }

    public void recordLoginSuccess() {
        this.loginFailCount = 0;
        this.lastLoginAt = LocalDateTime.now();
    }

    /** 이메일 인증으로 잠금을 푼다. 실패 횟수도 함께 초기화한다. */
    public void unlock() {
        if (this.status != AccountStatus.LOCKED) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_STATE);
        }
        this.status = AccountStatus.ACTIVE;
        this.lockedAt = null;
        this.loginFailCount = 0;
    }

    /**
     * 비밀번호를 교체한다.
     *
     * @param temporary 임시 비밀번호면 true. 다음 로그인에서 변경을 강제한다.
     */
    public void changePassword(String newPasswordHash, boolean temporary) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.passwordHash = newPasswordHash;
        this.tempPassword = temporary;
        this.passwordUpdatedAt = LocalDateTime.now();
        this.loginFailCount = 0;
        // 임시 비밀번호 발급은 잠금 해제 수단이기도 하므로 잠금 상태를 함께 푼다.
        if (this.status == AccountStatus.LOCKED) {
            this.status = AccountStatus.ACTIVE;
            this.lockedAt = null;
        }
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    /** 마이페이지에서 전화번호를 수정한다. */
    public void updatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.phone = phone;
    }

    // ==========================================
    // 조회용 판정
    // ==========================================

    public boolean isLocked() {
        return this.status == AccountStatus.LOCKED;
    }

    public boolean isWithdrawn() {
        return this.status == AccountStatus.WITHDRAWN || this.withdrawnAt != null;
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public boolean isSocialOnly() {
        return this.signupType == SignupType.SOCIAL && this.passwordHash == null;
    }

    public boolean hasRole(Role expected) {
        return this.role == expected;
    }

    // ==========================================
    // 불변식
    // ==========================================

    private void validateRequired(String email, Role role, String name, String phone) {
        if (email == null || email.isBlank()
                || role == null
                || name == null || name.isBlank()
                || phone == null || phone.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
    }

    private void validatePasswordPresence(String passwordHash, SignupType signupType) {
        // 이메일 가입인데 비밀번호가 없으면 로그인 수단이 아예 없는 계정이 만들어진다.
        if (signupType == SignupType.EMAIL && (passwordHash == null || passwordHash.isBlank())) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
    }
}
