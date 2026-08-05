package com.pairing.auth.exception;

import com.pairing.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 인증 도메인 에러코드.
 *
 * <p>로그인 실패는 이유를 구분하지 않고 모두 {@link #LOGIN_FAILED} 하나로 응답한다.
 * "이메일은 있는데 비밀번호가 틀림"을 알려주면 가입 여부를 확인하는 통로가 된다. (요구사항 R14)
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AU_001", "ID나 PW가 일치하지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AU_002", "비밀번호를 5회 틀려 계정이 잠겼습니다. 이메일 인증 후 이용해 주세요."),
    EMAIL_SEND_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AU_003", "이메일 인증 요청 횟수를 초과했습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AU_004", "인증코드가 일치하지 않습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AU_005", "인증코드가 만료되었습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AU_006", "이메일 인증을 완료해 주세요."),
    DUPLICATED_EMAIL(HttpStatus.CONFLICT, "AU_007", "이미 가입된 이메일입니다."),
    DUPLICATED_PHONE(HttpStatus.CONFLICT, "AU_008", "이미 가입된 휴대폰번호입니다."),
    DUPLICATED_BUSINESS_NO(HttpStatus.CONFLICT, "AU_009", "이미 등록된 사업자등록번호입니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "AU_010",
            "비밀번호는 대소문자, 숫자, 특수문자를 포함해 8자 이상 20자 이하여야 합니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "AU_011", "비밀번호가 일치하지 않습니다."),
    VERIFICATION_ATTEMPT_EXCEEDED(HttpStatus.BAD_REQUEST, "AU_012", "인증 시도 횟수를 초과했습니다. 인증코드를 다시 요청해 주세요."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "AU_013", "필수 약관에 동의해야 합니다."),
    LOGIN_BLOCKED(HttpStatus.TOO_MANY_REQUESTS, "AU_014", "로그인 시도가 많아 접근이 제한되었습니다."),
    SESSION_TERMINATED(HttpStatus.UNAUTHORIZED, "AU_015", "다른 기기에서 로그인되어 로그아웃되었습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AU_016", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요."),
    TEMP_PASSWORD_MUST_BE_CHANGED(HttpStatus.FORBIDDEN, "AU_017", "임시 비밀번호 상태입니다. 비밀번호를 변경해 주세요."),
    SOCIAL_AUTH_FAILED(HttpStatus.BAD_REQUEST, "AU_018", "소셜 인증에 실패했습니다."),
    SOCIAL_ALREADY_LINKED(HttpStatus.CONFLICT, "AU_019", "이미 연동된 소셜 계정입니다."),
    SIGNUP_TICKET_EXPIRED(HttpStatus.BAD_REQUEST, "AU_020", "가입 정보가 만료되었습니다. 처음부터 다시 진행해 주세요."),
    REJOIN_RESTRICTED(HttpStatus.FORBIDDEN, "AU_021", "탈퇴 후 30일이 지나야 재가입할 수 있습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "AU_022", "이용이 정지된 계정입니다."),
    SOCIAL_NOT_ALLOWED_FOR_CLIENT(HttpStatus.BAD_REQUEST, "AU_023", "클라이언트는 소셜 로그인을 사용할 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "AU_024", "일치하는 회원 정보가 없습니다."),
    UNDER_MINIMUM_AGE(HttpStatus.BAD_REQUEST, "AU_025", "만 18세 미만은 가입할 수 없습니다."),
    MAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AU_026", "메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AU_027", "비밀번호 재설정 링크가 만료되었거나 유효하지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "AU_028", "현재 비밀번호와 다른 비밀번호를 입력해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
