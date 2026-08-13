package com.pairing.account.presentation.api;

import com.pairing.account.application.command.WithdrawAccountCommand;
import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.application.usecase.WithdrawalEligibilityUseCase;
import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.account.presentation.api.request.AccountSuspendRequest;
import com.pairing.account.presentation.api.request.AccountWithdrawRequest;
import com.pairing.account.presentation.api.request.BankAccountUpdateRequest;
import com.pairing.account.presentation.api.request.CardUpdateRequest;
import com.pairing.account.presentation.api.response.AdminAccountDetailResponse;
import com.pairing.account.presentation.api.response.AdminAccountResponse;
import com.pairing.account.presentation.api.response.AdminAccountSummaryResponse;
import com.pairing.account.presentation.api.response.PaymentMethodResponse;
import com.pairing.account.presentation.api.response.WithdrawalEligibilityResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final WithdrawalEligibilityUseCase withdrawalEligibilityUseCase;

    // ==========================================
    // 결제수단 (마이페이지 > 결제수단)
    // ==========================================

    @GetMapping("/me/payment-methods")
    @Operation(summary = "결제수단 목록",
            description = "수수료 결제 카드 1건 + 용역비 수령 계좌 1건을 반환합니다. 둘 다 가입 시 만들어집니다. "
                    + "수수료 결제 화면은 methodType 이 CARD 인 건만 사용하세요.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> findMyPaymentMethods(
            @CurrentAccountId Long accountId
    ) {
        List<PaymentMethodResponse> data = accountQueryUseCase.findMyPaymentMethods(accountId).stream()
                .map(PaymentMethodResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("PAYMENT_METHODS_FOUND", "조회에 성공했습니다.", data));
    }

    @PutMapping("/me/payment-methods/card")
    @Operation(summary = "카드 정보 수정",
            description = "가입 시 등록된 카드를 수정합니다. 신규 등록·삭제 API는 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodeExample(domain = AccountErrorCode.class, value = {"PAYMENT_METHOD_NOT_FOUND"})
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> updateCard(
            @Valid @RequestBody CardUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        PaymentMethodResponse data = PaymentMethodResponse.from(
                accountCommandUseCase.updateCard(accountId, request.toCommand()));
        return ResponseEntity.ok(ApiResponse.success("CARD_UPDATED", "카드 정보를 수정했습니다.", data));
    }

    @PutMapping("/me/payment-methods/bank-account")
    @Operation(summary = "계좌 정보 수정",
            description = "가입 시 등록된 계좌를 수정합니다. 신규 등록·삭제 API는 없습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodeExample(domain = AccountErrorCode.class,
            value = {"UNKNOWN_BANK_CODE", "PAYMENT_METHOD_NOT_FOUND"})
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> updateBankAccount(
            @Valid @RequestBody BankAccountUpdateRequest request,
            @CurrentAccountId Long accountId
    ) {
        PaymentMethodResponse data = PaymentMethodResponse.from(
                accountCommandUseCase.updateBankAccount(accountId, request.toCommand()));
        return ResponseEntity.ok(ApiResponse.success("BANK_ACCOUNT_UPDATED", "계좌 정보를 수정했습니다.", data));
    }

    // ==========================================
    // 탈퇴
    // ==========================================

    @GetMapping("/me/withdrawal-eligibility")
    @Operation(summary = "탈퇴 가능 여부 조회",
            description = "회원 탈퇴 화면 진입 시 호출합니다. withdrawable 이 false 면 탈퇴 버튼을 열지 말고 "
                    + "blockers 를 안내로 그려주세요. 문구(label)와 이동 경로(linkUrl)는 서버가 내려줍니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<WithdrawalEligibilityResponse>> findWithdrawalEligibility(
            @CurrentAccountId Long accountId
    ) {
        WithdrawalEligibilityResponse data = WithdrawalEligibilityResponse.from(
                withdrawalEligibilityUseCase.getWithdrawalEligibility(accountId));
        return ResponseEntity.ok(ApiResponse.success("WITHDRAWAL_ELIGIBILITY_FOUND", "조회에 성공했습니다.", data));
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴",
            description = "진행 중인 프로젝트나 미납 요금이 있으면 탈퇴할 수 없습니다. "
                    + "탈퇴 후 30일간 같은 이메일·휴대폰으로 재가입할 수 없습니다. "
                    + "agreed 는 true, confirmText 는 \"탈퇴하겠습니다\" 여야 합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodeExample(domain = AccountErrorCode.class,
            value = {"ALREADY_WITHDRAWN", "WITHDRAW_CONFIRM_MISMATCH",
                    "WITHDRAW_BLOCKED_BY_PROJECT", "WITHDRAW_BLOCKED_BY_SETTLEMENT"})
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @Valid @RequestBody AccountWithdrawRequest request,
            @CurrentAccountId Long accountId
    ) {
        accountCommandUseCase.withdraw(
                new WithdrawAccountCommand(accountId, request.confirmText(), request.reason()));
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
