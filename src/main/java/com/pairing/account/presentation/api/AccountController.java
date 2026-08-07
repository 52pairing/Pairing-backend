package com.pairing.account.presentation.api;

import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import com.pairing.account.presentation.api.request.AccountSuspendRequest;
import com.pairing.account.presentation.api.request.AccountWithdrawRequest;
import com.pairing.account.presentation.api.request.PaymentMethodCreateRequest;
import com.pairing.account.presentation.api.response.AdminAccountDetailResponse;
import com.pairing.account.presentation.api.response.AdminAccountResponse;
import com.pairing.account.presentation.api.response.AdminAccountSummaryResponse;
import com.pairing.account.presentation.api.response.PaymentMethodResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 계정 공통 기능. (요구사항 R17, R31, R37)
 *
 * <p>역할과 무관하게 같은 동작인 것만 둔다. 역할별 마이페이지 조회·수정은
 * freelancer / client 도메인이 담당한다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "06. Account", description = "계정 공통 API")
public class AccountController {

    /** 계정당 등록 가능한 수수료 결제수단 수. 마이페이지 안내 문구와 같은 값이다. */
    private static final int MAX_PAYMENT_METHOD_COUNT = 3;

    // ==========================================
    // 결제수단 (마이페이지 > 결제수단)
    // ==========================================

    @GetMapping("/me/payment-methods")
    @Operation(summary = "결제수단 목록",
            description = "수수료 결제에 쓰는 카드·간편결제 목록입니다. 기본 결제수단이 맨 앞에 옵니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> findMyPaymentMethods(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 삭제되지 않은 결제수단 조회 (기본 결제수단 우선 정렬)
        return ResponseEntity.ok(ApiResponse.success("PAYMENT_METHODS_FOUND", "조회에 성공했습니다.",
                List.of(sampleCard(), sampleEasyPay())));
    }

    @PostMapping("/me/payment-methods")
    @Operation(summary = "결제수단 등록",
            description = "최대 " + MAX_PAYMENT_METHOD_COUNT + "개까지 등록할 수 있습니다. 첫 등록분이 기본 결제수단이 됩니다. "
                    + "CVC는 저장하지 않습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> addPaymentMethod(
            @Valid @RequestBody PaymentMethodCreateRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 등록 개수 확인 -> methodType 별 필수값 검증 -> 숫자만 정규화 후 암호화 저장
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("PAYMENT_METHOD_ADDED", "결제수단을 등록했습니다.", sampleCard()));
    }

    @PutMapping("/me/payment-methods/{paymentMethodId}/default")
    @Operation(summary = "기본 결제수단 설정", description = "기존 기본 결제수단은 자동으로 해제됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"API_NOT_FOUND", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> setDefaultPaymentMethod(
            @PathVariable Long paymentMethodId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 본인 소유 확인 -> 기존 기본 해제 -> 대상 기본 설정
        return ResponseEntity.ok(ApiResponse.success("DEFAULT_PAYMENT_METHOD_CHANGED", "기본 결제수단을 변경했습니다.",
                List.of(sampleCard(), sampleEasyPay())));
    }

    @DeleteMapping("/me/payment-methods/{paymentMethodId}")
    @Operation(summary = "결제수단 삭제",
            description = "미납 정산이 남아 있는 기본 결제수단은 삭제할 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<Void>> deletePaymentMethod(
            @PathVariable Long paymentMethodId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 본인 소유 확인 -> 미납 정산 확인 -> soft delete -> 기본이었다면 다음 수단으로 승계
        return ResponseEntity.ok(ApiResponse.success("PAYMENT_METHOD_DELETED", "결제수단을 삭제했습니다."));
    }

    // ==========================================
    // 탈퇴
    // ==========================================

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴",
            description = "진행 중인 프로젝트나 미납 요금이 있으면 탈퇴할 수 없습니다. 탈퇴 후 30일간 같은 이메일·휴대폰으로 재가입할 수 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Valid @RequestBody AccountWithdrawRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 진행 중 프로젝트·미납 확인 -> 이메일 더미 치환 + 해시 보관 -> 세션 파기
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_WITHDRAWN", "탈퇴가 완료되었습니다."));
    }

    // ==========================================
    // 관리자 (R37)
    // ==========================================

    @GetMapping("/admin/summary")
    @Operation(summary = "[관리자] 회원 요약", description = "회원 관리 화면 상단 카드입니다.")
    public ResponseEntity<ApiResponse<AdminAccountSummaryResponse>> findSummaryForAdmin() {
        // TODO: 상태별·역할별 집계
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_SUMMARY_FOUND", "조회에 성공했습니다.",
                new AdminAccountSummaryResponse(6, 4, 1, 1, 3, 3)));
    }

    @GetMapping("/admin")
    @Operation(summary = "[관리자] 회원 목록",
            description = "이름·이메일 검색과 유형·상태·가입방식 필터를 지원합니다.")
    public ResponseEntity<ApiResponse<PageResponse<AdminAccountResponse>>> findAllForAdmin(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) SignupType signupType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 회원 검색
        return ResponseEntity.ok(ApiResponse.success("ACCOUNTS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleAdminAccount()), page, size, 1, 1, true, true)));
    }

    @GetMapping("/admin/{accountId}")
    @Operation(summary = "[관리자] 회원 상세",
            description = "기본 정보와 활동 현황(진행중·완료·취소·누적 거래금액·리뷰 수·평균 별점), 프로젝트 이력을 함께 반환합니다.")
    public ResponseEntity<ApiResponse<AdminAccountDetailResponse>> findOneForAdmin(
            @PathVariable Long accountId
    ) {
        // TODO: 계정 + 프로필 + 활동 집계 + 프로젝트 이력 조회
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_FOUND", "조회에 성공했습니다.",
                sampleAdminAccountDetail()));
    }

    @PostMapping("/admin/{accountId}/suspension")
    @Operation(summary = "[관리자] 회원 정지", description = "정지된 회원은 로그인할 수 없습니다.")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> suspend(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountSuspendRequest request
    ) {
        // TODO: Redis 정지 마커 등록 + 세션 파기
        return ResponseEntity.ok(ApiResponse.success("ACCOUNT_SUSPENDED", "회원을 정지했습니다.", sampleAdminAccount()));
    }

    @DeleteMapping("/admin/{accountId}/suspension")
    @Operation(summary = "[관리자] 회원 정지 해제")
    public ResponseEntity<ApiResponse<AdminAccountResponse>> releaseSuspension(@PathVariable Long accountId) {
        // TODO: Redis 정지 마커 삭제
        return ResponseEntity.ok(ApiResponse.success("SUSPENSION_RELEASED", "정지를 해제했습니다.", sampleAdminAccount()));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private PaymentMethodResponse sampleCard() {
        return new PaymentMethodResponse(300L, PaymentMethodType.CARD, "신한카드 **** 1234",
                "신한카드", "1234", "09/28", "김개발", true);
    }

    private PaymentMethodResponse sampleEasyPay() {
        return new PaymentMethodResponse(301L, PaymentMethodType.EASY_PAY, "카카오페이 · 계좌 연동",
                null, null, null, null, false);
    }

    private AdminAccountDetailResponse sampleAdminAccountDetail() {
        return new AdminAccountDetailResponse(7L, "MEM-001", Role.CLIENT, "김담당", "hr@samsung.com",
                "02-1234-5678", SignupType.EMAIL, LocalDate.of(2026, 1, 15), LocalDateTime.now(),
                AccountStatus.ACTIVE, false, null,
                "삼성전자", "123-45-67890", "IT·콘텐츠·AI", "500명 이상",
                new AdminAccountDetailResponse.Activity(2, 2, 0, 10_000_000L, 3, 4.8),
                List.of(new AdminAccountDetailResponse.ProjectHistory(1L, "PRJ-001",
                                "쇼핑몰 관리자 페이지 리뉴얼", "진행중", "월 5,000,000원", LocalDate.of(2026, 7, 25)),
                        new AdminAccountDetailResponse.ProjectHistory(4L, "PRJ-004",
                                "데이터 파이프라인 구축", "종료", "월 4,800,000원", LocalDate.of(2026, 5, 15))));
    }

    private AdminAccountResponse sampleAdminAccount() {
        return new AdminAccountResponse(7L, "MEM-001", Role.CLIENT, "삼성전자", "김담당",
                "hr@samsung.com", SignupType.EMAIL, AccountStatus.ACTIVE, false, null, 2,
                LocalDateTime.now().minusMonths(3), LocalDateTime.now());
    }
}
