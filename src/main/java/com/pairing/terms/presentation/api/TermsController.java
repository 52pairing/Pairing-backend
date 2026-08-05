package com.pairing.terms.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.terms.application.usecase.TermsQueryUseCase;
import com.pairing.terms.presentation.api.response.TermsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 회원가입 화면이 로그인 전에 호출한다. (GlobalSecurityConfig에서 permitAll) */
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
@Validated
@Tag(name = "03. Terms", description = "약관 조회 API")
public class TermsController {

    private final TermsQueryUseCase termsQueryUseCase;

    @GetMapping
    @Operation(summary = "역할별 최신 약관 목록", description = "가입 화면에 노출할 약관을 코드별 최신 버전으로 반환합니다.")
    public ResponseEntity<ApiResponse<List<TermsResponse>>> getTerms(
            @RequestParam
            @Pattern(regexp = "CLIENT|FREELANCER", message = "role은 CLIENT 또는 FREELANCER 여야 합니다.")
            String role
    ) {
        List<TermsResponse> responseData = termsQueryUseCase.findLatestByRole(role).stream()
                .map(terms -> new TermsResponse(
                        terms.getId(),
                        terms.getCode().name(),
                        terms.getTitle(),
                        terms.getVersion(),
                        terms.isRequired(),
                        terms.getContent()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("TERMS_FOUND", "조회에 성공했습니다.", responseData));
    }
}
