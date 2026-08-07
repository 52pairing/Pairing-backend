package com.pairing.terms.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.terms.application.usecase.TermsQueryUseCase;
import com.pairing.terms.domain.model.Terms;
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

/**
 * 약관 조회. 회원가입 화면과 푸터가 로그인 전에 호출한다. (GlobalSecurityConfig에서 permitAll)
 *
 * <p>가입 화면에 뜨는 동의 항목은 세 개다.
 * 서비스 이용약관(필수) / 개인정보 수집 및 이용 동의(필수) / 마케팅 정보 수신 동의(선택).
 *
 * <p>개인정보 처리방침은 동의 항목이 아니다. 보호법 제30조상 '수립·공개' 의무라
 * {@code /documents} 로만 나가고 가입 화면에는 뜨지 않는다.
 */
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
@Validated
@Tag(name = "03. Terms", description = "약관 조회 API")
public class TermsController {

    private final TermsQueryUseCase termsQueryUseCase;

    @GetMapping
    @Operation(summary = "가입 동의 항목 조회",
            description = "가입 화면에 노출할 동의 항목을 코드별 최신 버전으로 반환합니다. "
                    + "개인정보 처리방침은 동의 대상이 아니라 여기 포함되지 않습니다.")
    public ResponseEntity<ApiResponse<List<TermsResponse>>> getTerms(
            @RequestParam
            @Pattern(regexp = "CLIENT|FREELANCER", message = "role은 CLIENT 또는 FREELANCER 여야 합니다.")
            String role
    ) {
        List<TermsResponse> responseData = termsQueryUseCase.findLatestByRole(role).stream()
                .map(TermsController::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("TERMS_FOUND", "조회에 성공했습니다.", responseData));
    }

    @GetMapping("/documents")
    @Operation(summary = "약관·방침 전문 조회",
            description = "푸터의 문서 링크용입니다. 동의 항목 전문과 개인정보 처리방침을 함께 반환합니다.")
    public ResponseEntity<ApiResponse<List<TermsResponse>>> getDocuments(
            @RequestParam
            @Pattern(regexp = "CLIENT|FREELANCER", message = "role은 CLIENT 또는 FREELANCER 여야 합니다.")
            String role
    ) {
        List<TermsResponse> responseData = termsQueryUseCase.findLatestDocuments(role).stream()
                .map(TermsController::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("TERMS_DOCUMENTS_FOUND", "조회에 성공했습니다.", responseData));
    }

    private static TermsResponse toResponse(Terms terms) {
        return new TermsResponse(
                terms.getId(),
                terms.getCode().name(),
                terms.getType(),
                terms.getTitle(),
                terms.getVersion(),
                terms.isRequired(),
                terms.getEffectiveAt(),
                terms.getContent());
    }
}
