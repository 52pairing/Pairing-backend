package com.pairing.auth.presentation.api;

import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.SendCodeCommand;
import com.pairing.auth.application.result.SendCodeResult;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.presentation.api.request.ConfirmCodeRequest;
import com.pairing.auth.presentation.api.request.SendCodeRequest;
import com.pairing.auth.presentation.api.response.SendCodeResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이메일 인증코드 발송/확인.
 *
 * <p>코드 유효시간은 3분, 발송은 1시간에 15회까지다. 확인에 성공하면 30분 동안
 * "인증 완료" 상태가 유지되고, 그 안에 가입을 제출해야 한다.
 */
@RestController
@RequestMapping("/api/v1/auth/email-verifications")
@RequiredArgsConstructor
@Tag(name = "01. Auth - Email", description = "이메일 인증 API")
public class EmailVerificationController {

    private final EmailVerificationUseCase emailVerificationUseCase;

    @PostMapping
    @Operation(summary = "인증코드 발송", description = "6자리 코드를 메일로 보냅니다. 응답의 expiresAt으로 타이머를 표시하세요.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {"EMAIL_SEND_LIMIT_EXCEEDED", "MAIL_SEND_FAILED"})
    public ResponseEntity<ApiResponse<SendCodeResponse>> send(@Valid @RequestBody SendCodeRequest request) {
        SendCodeResult result = emailVerificationUseCase.send(
                new SendCodeCommand(request.email(), request.purpose()));

        return ResponseEntity.ok(ApiResponse.success("VERIFICATION_CODE_SENT", "인증코드를 발송했습니다.",
                new SendCodeResponse(result.expiresAt(), result.remainingSendCount())));
    }

    @PostMapping("/confirm")
    @Operation(summary = "인증코드 확인")
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "VERIFICATION_CODE_MISMATCH", "VERIFICATION_CODE_EXPIRED", "VERIFICATION_ATTEMPT_EXCEEDED"})
    public ResponseEntity<ApiResponse<Void>> confirm(@Valid @RequestBody ConfirmCodeRequest request) {
        emailVerificationUseCase.confirm(
                new ConfirmCodeCommand(request.email(), request.purpose(), request.code()));

        return ResponseEntity.ok(ApiResponse.success("VERIFICATION_CONFIRMED", "이메일 인증이 완료되었습니다."));
    }
}
