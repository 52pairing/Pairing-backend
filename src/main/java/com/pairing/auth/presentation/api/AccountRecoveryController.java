package com.pairing.auth.presentation.api;

import com.pairing.auth.application.command.ChangePasswordCommand;
import com.pairing.auth.application.usecase.AccountRecoveryUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.presentation.api.request.ChangePasswordRequest;
import com.pairing.auth.presentation.api.request.FindEmailRequest;
import com.pairing.auth.presentation.api.request.PasswordResetConfirmRequest;
import com.pairing.auth.presentation.api.request.PasswordResetRequest;
import com.pairing.auth.presentation.api.request.UnlockRequest;
import com.pairing.auth.application.result.MaskedEmailResult;
import com.pairing.auth.presentation.api.response.FindEmailResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 아이디 찾기 / 비밀번호 재설정 / 잠금 해제.
 *
 * <p>{@code /api/v1/auth/**} 는 permitAll 이므로, 로그인 상태에서만 허용해야 하는
 * 비밀번호 변경에는 @PreAuthorize 를 붙인다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "01. Auth - Recovery", description = "아이디/비밀번호 찾기 API")
public class AccountRecoveryController {

    private final AccountRecoveryUseCase accountRecoveryUseCase;
    private final AuthCookieWriter authCookieWriter;

    @PostMapping("/find-email")
    @Operation(summary = "아이디(이메일) 찾기",
            description = "이름과 전화번호로 찾습니다. 두 역할로 가입했다면 계정이 둘 다 나오며, 이메일은 마스킹됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"MEMBER_NOT_FOUND"})
    public ResponseEntity<ApiResponse<FindEmailResponse>> findEmail(@Valid @RequestBody FindEmailRequest request) {
        List<MaskedEmailResult> results = accountRecoveryUseCase.findMaskedEmails(request.toCommand());

        return ResponseEntity.ok(ApiResponse.success("EMAIL_FOUND", "조회에 성공했습니다.",
                FindEmailResponse.from(results)));
    }

    @PostMapping("/password/reset-requests")
    @Operation(summary = "비밀번호 재설정 링크 요청",
            description = "이메일/이름/전화번호가 모두 일치하면 3분짜리 링크를 보냅니다. 계정 열거를 막기 위해 일치하지 않아도 동일하게 응답합니다.")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        accountRecoveryUseCase.requestPasswordReset(request.toCommand());

        return ResponseEntity.ok(ApiResponse.success("PASSWORD_RESET_REQUESTED",
                "입력하신 정보와 일치하는 계정이 있으면 재설정 안내 메일을 보냈습니다."));
    }

    @PostMapping("/password/reset-confirm")
    @Operation(summary = "재설정 링크 확인 및 임시 비밀번호 발급",
            description = "링크의 토큰을 확인하고 임시 비밀번호를 메일로 보냅니다. 기존 로그인 세션은 모두 끊깁니다.")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"PASSWORD_RESET_TOKEN_INVALID", "MAIL_SEND_FAILED"})
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request
    ) {
        accountRecoveryUseCase.issueTempPassword(request.token());

        return ResponseEntity.ok(ApiResponse.success("TEMP_PASSWORD_ISSUED", "임시 비밀번호를 메일로 보냈습니다."));
    }

    @PatchMapping("/password")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "비밀번호 변경 (마이페이지)",
            description = "이메일 인증코드를 purpose=PASSWORD_CHANGE 로 먼저 확인한 뒤 호출합니다. "
                    + "현재 비밀번호는 받지 않고 새 비밀번호와 확인만 받습니다. "
                    + "변경 후에는 모든 세션이 끊겨 재로그인이 필요합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "EMAIL_NOT_VERIFIED", "INVALID_PASSWORD_FORMAT", "PASSWORD_CONFIRM_MISMATCH",
            "SAME_AS_CURRENT_PASSWORD", "SOCIAL_ACCOUNT_NO_PASSWORD"})
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @CurrentAccountId Long accountId,
            HttpServletResponse response
    ) {
        accountRecoveryUseCase.changePassword(new ChangePasswordCommand(
                accountId,
                request.newPassword(),
                request.newPasswordConfirm()
        ));

        // 세션을 끊었으므로 쿠키도 함께 정리한다. 프론트는 로그인 화면으로 보낸다.
        authCookieWriter.clear(response);

        return ResponseEntity.ok(ApiResponse.success("PASSWORD_CHANGED", "비밀번호가 변경되었습니다. 다시 로그인해 주세요."));
    }

    @PostMapping("/unlock")
    @Operation(summary = "계정 잠금 해제", description = "UNLOCK 용도로 받은 인증코드를 확인하고 잠금을 풉니다.")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "VERIFICATION_CODE_MISMATCH", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_ATTEMPT_EXCEEDED",
            "MEMBER_NOT_FOUND"})
    public ResponseEntity<ApiResponse<Void>> unlock(@Valid @RequestBody UnlockRequest request) {
        accountRecoveryUseCase.unlock(request.toCommand());

        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_UNLOCKED", "잠금이 해제되었습니다. 다시 로그인해 주세요."));
    }
}
