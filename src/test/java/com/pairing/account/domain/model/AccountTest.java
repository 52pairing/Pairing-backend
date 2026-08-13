package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 계정 상태 전이 규칙. 로그인 잠금과 비밀번호 교체가 여기서 결정된다. */
class AccountTest {

    private static final int LOCK_THRESHOLD = 5;

    private Account emailAccount() {
        return Account.createByEmail("user@pairing.com", "$2a$10$hash", Role.FREELANCER, "홍길동", "01012345678");
    }

    @Test
    @DisplayName("이메일 가입 계정은 ACTIVE + 이메일 인증 완료 상태로 만들어진다")
    void createByEmail() {
        Account account = emailAccount();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getSignupType()).isEqualTo(SignupType.EMAIL);
        assertThat(account.isTempPassword()).isFalse();
        assertThat(account.isSocialOnly()).isFalse();
    }

    @Test
    @DisplayName("이메일 가입인데 비밀번호가 없으면 로그인 수단이 없는 계정이므로 생성을 막는다")
    void rejectsEmailAccountWithoutPassword() {
        assertThatThrownBy(() ->
                Account.createByEmail("user@pairing.com", null, Role.FREELANCER, "홍길동", "01012345678"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_FIELD);
    }

    @Test
    @DisplayName("소셜 계정은 비밀번호가 없고 프리랜서만 만들 수 있다")
    void createBySocial() {
        Account account = Account.createBySocial(
                "user@gmail.com", Role.FREELANCER, "홍길동", "01012345678", true);

        assertThat(account.isSocialOnly()).isTrue();
        assertThat(account.getPasswordHash()).isNull();

        assertThatThrownBy(() ->
                Account.createBySocial("owner@pairing.com", Role.CLIENT, "홍길동", "01012345678", true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.SOCIAL_NOT_ALLOWED_FOR_ROLE);
    }

    @Test
    @DisplayName("비밀번호를 5회 틀리면 잠긴다")
    void locksAfterThresholdFailures() {
        Account account = emailAccount();

        for (int i = 0; i < LOCK_THRESHOLD - 1; i++) {
            account.recordLoginFailure(LOCK_THRESHOLD);
        }
        assertThat(account.isLocked()).isFalse();

        account.recordLoginFailure(LOCK_THRESHOLD);

        assertThat(account.isLocked()).isTrue();
        assertThat(account.getLockedAt()).isNotNull();
        assertThat(account.getLoginFailCount()).isEqualTo(LOCK_THRESHOLD);
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수가 초기화된다")
    void resetsFailureCountOnSuccess() {
        Account account = emailAccount();
        account.recordLoginFailure(LOCK_THRESHOLD);
        account.recordLoginFailure(LOCK_THRESHOLD);

        account.recordLoginSuccess();

        assertThat(account.getLoginFailCount()).isZero();
        assertThat(account.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("잠금 해제는 잠긴 계정에만 할 수 있다")
    void unlockOnlyWhenLocked() {
        Account account = emailAccount();

        assertThatThrownBy(account::unlock)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATE);

        for (int i = 0; i < LOCK_THRESHOLD; i++) {
            account.recordLoginFailure(LOCK_THRESHOLD);
        }
        account.unlock();

        assertThat(account.isActive()).isTrue();
        assertThat(account.getLoginFailCount()).isZero();
        assertThat(account.getLockedAt()).isNull();
    }

    @Test
    @DisplayName("임시 비밀번호를 발급하면 잠금이 함께 풀리고 변경 강제 표시가 붙는다")
    void changePasswordUnlocksAccount() {
        Account account = emailAccount();
        for (int i = 0; i < LOCK_THRESHOLD; i++) {
            account.recordLoginFailure(LOCK_THRESHOLD);
        }

        account.changePassword("$2a$10$temporary", true);

        assertThat(account.isTempPassword()).isTrue();
        assertThat(account.isLocked()).isFalse();
        assertThat(account.getLoginFailCount()).isZero();
        assertThat(account.getPasswordUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("빈 비밀번호로는 교체할 수 없다")
    void rejectsBlankPassword() {
        Account account = emailAccount();

        assertThatThrownBy(() -> account.changePassword("  ", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("탈퇴하면 이메일·휴대폰이 더미로 바뀌고 원본은 해시로만 남는다")
    void withdrawReplacesContactWithPlaceholder() {
        Account account = emailAccount();
        LocalDateTime rejoinAt = LocalDateTime.now().plusDays(30);
        LocalDateTime purgeAt = LocalDateTime.now().plusYears(5);

        account.withdraw("서비스를 더 이용하지 않습니다.",
                "withdrawn-1@withdrawn.pairing.invalid", "withdrawn-1",
                "email-hash", "phone-hash", rejoinAt, purgeAt);

        // 원본 연락처가 남아 있으면 탈퇴했는데 개인정보가 살아 있는 것이다
        assertThat(account.getEmail()).isEqualTo("withdrawn-1@withdrawn.pairing.invalid");
        assertThat(account.getPhone()).isEqualTo("withdrawn-1");
        assertThat(account.getEmailHash()).isEqualTo("email-hash");
        assertThat(account.getPhoneHash()).isEqualTo("phone-hash");

        // 남은 비밀번호 해시로 로그인이 시도될 여지를 없앤다
        assertThat(account.getPasswordHash()).isNull();

        assertThat(account.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(account.isWithdrawn()).isTrue();
        assertThat(account.isActive()).isFalse();
        assertThat(account.getWithdrawnAt()).isNotNull();
        assertThat(account.getDeletedAt()).isNotNull();
        assertThat(account.getRejoinAvailableAt()).isEqualTo(rejoinAt);
        assertThat(account.getPurgeAt()).isEqualTo(purgeAt);
    }

    @Test
    @DisplayName("이미 탈퇴한 계정은 다시 탈퇴할 수 없다")
    void rejectsDoubleWithdraw() {
        Account account = emailAccount();
        account.withdraw(null, "withdrawn-1@withdrawn.pairing.invalid", "withdrawn-1",
                "email-hash", "phone-hash", LocalDateTime.now(), LocalDateTime.now());

        assertThatThrownBy(() -> account.withdraw(null, "withdrawn-1@withdrawn.pairing.invalid", "withdrawn-1",
                "email-hash", "phone-hash", LocalDateTime.now(), LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.ALREADY_WITHDRAWN);
    }
}
