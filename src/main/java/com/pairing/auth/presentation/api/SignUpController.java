package com.pairing.auth.presentation.api;

import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.result.LoginResult;
import com.pairing.auth.application.usecase.SignUpUseCase;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.presentation.api.request.ClientSignUpRequest;
import com.pairing.auth.presentation.api.request.FreelancerSignUpRequest;
import com.pairing.auth.presentation.api.request.SocialSignUpRequest;
import com.pairing.auth.presentation.api.response.DuplicationResponse;
import com.pairing.auth.presentation.api.response.LoginResponse;
import com.pairing.auth.presentation.api.response.SignUpResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.terms.exception.TermsErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입.
 *
 * <p>세 경로 모두 이메일 인증(소셜은 공급자 인증)과 필수 약관 동의를 마쳐야 통과한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "01. Auth - SignUp", description = "회원가입 API")
public class SignUpController {

    private final SignUpUseCase signUpUseCase;
    private final AuthCookieWriter authCookieWriter;

    @PostMapping("/signup/client")
    @Operation(summary = "클라이언트 회원가입", description = "기업 정보와 결제수단을 함께 등록합니다. 이메일 인증이 선행되어야 합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "EMAIL_NOT_VERIFIED", "DUPLICATED_EMAIL", "DUPLICATED_PHONE", "DUPLICATED_BUSINESS_NO",
            "INVALID_PASSWORD_FORMAT", "PASSWORD_CONFIRM_MISMATCH", "REQUIRED_TERMS_NOT_AGREED", "REJOIN_RESTRICTED"})
    @ApiErrorCodeExample(domain = TermsErrorCode.class, value = {"REQUIRED_TERMS_NOT_AGREED", "UNKNOWN_TERMS_INCLUDED"})
    public ResponseEntity<ApiResponse<SignUpResponse>> signUpClient(
            @Valid @RequestBody ClientSignUpRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        Long accountId = signUpUseCase.signUpClient(request.toCommand(userAgent));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("CLIENT_SIGNED_UP", "회원가입이 완료되었습니다.",
                        new SignUpResponse(accountId, "CLIENT")));
    }

    @PostMapping("/signup/freelancer")
    @Operation(summary = "프리랜서 일반 회원가입", description = "이메일 인증과 만 18세 이상 조건을 확인합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "EMAIL_NOT_VERIFIED", "DUPLICATED_EMAIL", "DUPLICATED_PHONE", "INVALID_PASSWORD_FORMAT",
            "PASSWORD_CONFIRM_MISMATCH", "UNDER_MINIMUM_AGE", "REQUIRED_TERMS_NOT_AGREED", "REJOIN_RESTRICTED"})
    @ApiErrorCodeExample(domain = TermsErrorCode.class, value = {"REQUIRED_TERMS_NOT_AGREED", "UNKNOWN_TERMS_INCLUDED"})
    public ResponseEntity<ApiResponse<SignUpResponse>> signUpFreelancer(
            @Valid @RequestBody FreelancerSignUpRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        Long accountId = signUpUseCase.signUpFreelancer(request.toCommand(userAgent));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("FREELANCER_SIGNED_UP", "회원가입이 완료되었습니다.",
                        new SignUpResponse(accountId, "FREELANCER")));
    }

    @PostMapping("/signup/freelancer/social")
    @Operation(summary = "프리랜서 소셜 회원가입",
            description = "소셜 콜백에서 받은 티켓으로 가입을 마칩니다. 이메일은 티켓 값이 쓰이며 변경할 수 없습니다. 가입 직후 로그인 쿠키가 발급됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = AuthErrorCode.class, value = {
            "SIGNUP_TICKET_EXPIRED", "DUPLICATED_EMAIL", "DUPLICATED_PHONE", "SOCIAL_ALREADY_LINKED",
            "UNDER_MINIMUM_AGE", "REQUIRED_TERMS_NOT_AGREED", "REJOIN_RESTRICTED"})
    public ResponseEntity<ApiResponse<LoginResponse>> signUpFreelancerBySocial(
            @Valid @RequestBody SocialSignUpRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse response
    ) {
        LoginResult result = signUpUseCase.signUpFreelancerBySocial(request.toCommand(userAgent));
        authCookieWriter.write(response, result);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("FREELANCER_SIGNED_UP", "회원가입이 완료되었습니다.",
                        LoginResponse.from(result)));
    }

    @GetMapping("/exists/email")
    @Operation(summary = "이메일 중복 확인",
            description = "역할별로 판정합니다. 클라이언트로 쓰인 이메일이어도 프리랜서로는 가입할 수 있습니다.")
    public ResponseEntity<ApiResponse<DuplicationResponse>> checkEmail(
            @RequestParam String email,
            @RequestParam Role role
    ) {
        return ResponseEntity.ok(ApiResponse.success("EMAIL_CHECKED", "확인에 성공했습니다.",
                new DuplicationResponse(signUpUseCase.isEmailDuplicated(email, role))));
    }

    @GetMapping("/exists/phone")
    @Operation(summary = "휴대폰번호 중복 확인", description = "역할별로 판정합니다.")
    public ResponseEntity<ApiResponse<DuplicationResponse>> checkPhone(
            @RequestParam String phone,
            @RequestParam Role role
    ) {
        return ResponseEntity.ok(ApiResponse.success("PHONE_CHECKED", "확인에 성공했습니다.",
                new DuplicationResponse(signUpUseCase.isPhoneDuplicated(phone, role))));
    }

    @GetMapping("/exists/business-no")
    @Operation(summary = "사업자등록번호 중복 확인", description = "형식 검사만 하며, 국세청 진위확인은 아직 연동하지 않았습니다.")
    public ResponseEntity<ApiResponse<DuplicationResponse>> checkBusinessNo(
            @RequestParam
            @Pattern(regexp = "^\\d{10}$", message = "사업자등록번호는 하이픈 없이 숫자 10자리입니다.")
            String businessNo
    ) {
        return ResponseEntity.ok(ApiResponse.success("BUSINESS_NO_CHECKED", "확인에 성공했습니다.",
                new DuplicationResponse(signUpUseCase.isBusinessNoDuplicated(businessNo))));
    }
}
