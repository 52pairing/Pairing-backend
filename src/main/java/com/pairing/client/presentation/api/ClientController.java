package com.pairing.client.presentation.api;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.client.presentation.api.request.ClientProfileUpdateRequest;
import com.pairing.client.presentation.api.response.ClientMyPageResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라이언트 마이페이지. (요구사항 R31)
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "21. Client", description = "클라이언트 마이페이지 API")
public class ClientController {

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "마이페이지 조회", description = "기업 정보와 등급·평점 요약을 함께 반환합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<ClientMyPageResponse>> findMe(@CurrentAccountId Long accountId) {
        // TODO: 계정 + 기업 프로필 + 결제수단 마스킹 + 리뷰 집계 조회
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_FOUND", "조회에 성공했습니다.", sampleMyPage()));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "마이페이지 수정",
            description = "이메일 인증(purpose=PROFILE_UPDATE)을 마친 뒤 호출해야 합니다. 사업자등록번호·사업 분야·이메일·대표자명은 수정할 수 없습니다.")
    public ResponseEntity<ApiResponse<ClientMyPageResponse>> updateMe(
            @Valid @RequestBody ClientProfileUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 이메일 인증 마커 확인 후 수정
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_UPDATED", "수정되었습니다.", sampleMyPage()));
    }

    private ClientMyPageResponse sampleMyPage() {
        return new ClientMyPageResponse(3L, "주식회사 페어링", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_10_49, "owner@pairing.com",
                "홍길동", "01012345678", "서울 강남구", "logos/uuid.png",
                ClientGrade.SILVER, 4.2, 8, true);
    }
}
