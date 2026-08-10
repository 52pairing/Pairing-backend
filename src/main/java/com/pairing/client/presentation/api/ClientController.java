package com.pairing.client.presentation.api;

import com.pairing.client.application.usecase.ClientCommandUseCase;
import com.pairing.client.application.usecase.ClientQueryUseCase;
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
 * 클라이언트 마이페이지(기업정보). (요구사항 R31)
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "21. Client", description = "클라이언트 마이페이지 API")
public class ClientController {

    private final ClientQueryUseCase clientQueryUseCase;
    private final ClientCommandUseCase clientCommandUseCase;

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "마이페이지 조회", description = "기업 정보와 등급·평점 요약을 함께 반환합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<ClientMyPageResponse>> findMe(@CurrentAccountId Long accountId) {
        ClientMyPageResponse response = ClientMyPageResponse.from(clientQueryUseCase.findMyPage(accountId));
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_FOUND", "조회에 성공했습니다.", response));
    }

    @PatchMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "마이페이지 수정",
            description = "사업자등록번호·사업 분야·업무이메일·담당자명은 수정할 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<ClientMyPageResponse>> updateMe(
            @Valid @RequestBody ClientProfileUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        ClientMyPageResponse response =
                ClientMyPageResponse.from(clientCommandUseCase.updateMyPage(request.toCommand(accountId)));
        return ResponseEntity.ok(ApiResponse.success("MY_PAGE_UPDATED", "수정되었습니다.", response));
    }
}
