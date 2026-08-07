package com.pairing.admin.presentation.api;

import com.pairing.admin.presentation.api.response.AdminDashboardResponse;
import com.pairing.global.common.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드. (관리자 &gt; 대시보드)
 *
 * <p>도메인별 관리 화면의 요약을 한 번에 모아 준다. 여러 도메인을 가로지르는 조회라
 * 특정 도메인에 두지 않고 admin 패키지에 별도로 뒀다.
 *
 * <p>경로가 {@code /api/v1/admin/**} 이라 시큐리티 설정에서 ROLE_ADMIN 으로 막힌다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "30. Admin", description = "관리자 대시보드 API")
public class AdminDashboardController {

    @GetMapping("/dashboard")
    @Operation(summary = "[관리자] 대시보드",
            description = "회원·프로젝트·협상·정산 요약과 처리 대기 항목을 반환합니다.")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> findDashboard() {
        // TODO: 도메인별 집계 쿼리를 모아 조립
        return ResponseEntity.ok(ApiResponse.success("DASHBOARD_FOUND", "조회에 성공했습니다.", sampleDashboard()));
    }

    private AdminDashboardResponse sampleDashboard() {
        return new AdminDashboardResponse(
                LocalDate.now(),
                new AdminDashboardResponse.Members(6, 2, 1),
                new AdminDashboardResponse.Projects(12, 4, 3),
                new AdminDashboardResponse.Negotiations(1, 2, 1),
                new AdminDashboardResponse.Settlements(125_000_000L, 8_400_000L, 450_000L),
                List.of(new AdminDashboardResponse.PendingItem("INQUIRY", "답변 대기 문의", 3),
                        new AdminDashboardResponse.PendingItem("SITE_REVIEW", "공개 검토 대기 리뷰", 2),
                        new AdminDashboardResponse.PendingItem("SETTLEMENT", "미납 정산", 1)));
    }
}
