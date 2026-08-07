package com.pairing.settlement.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.PenaltyStatus;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.presentation.api.request.SettlementPayRequest;
import com.pairing.settlement.presentation.api.response.PenaltyResponse;
import com.pairing.settlement.presentation.api.response.SettlementResponse;
import com.pairing.settlement.presentation.api.response.SettlementSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 수수료 정산과 위약금. (요구사항 R20, R34, R39)
 *
 * <p>실제 용역비는 플랫폼을 거치지 않는다. 여기서 다루는 돈은 플랫폼 수수료와 위약금뿐이다.
 * 결제는 PG 연동 없이 가상계좌 잔액을 증감하는 수동 처리다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
@Tag(name = "15. Settlement", description = "정산/결제 API")
public class SettlementController {

    @GetMapping("/mine")
    @Operation(summary = "내 정산 목록", description = "착수금·성공보수 수수료 내역입니다. 클라이언트와 프리랜서 모두 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> findMine(
            @RequestParam(required = false) SettlementPhase phase,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내가 납부자인 정산 조회
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENTS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleSettlement()), page, size, 1, 1, true, true)));
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "정산 상세")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<SettlementResponse>> findOne(
            @PathVariable Long settlementId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 납부자 본인 또는 관리자만 열람
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_FOUND", "조회에 성공했습니다.", sampleSettlement()));
    }

    @PostMapping("/{settlementId}/payment")
    @Operation(summary = "수수료 결제",
            description = "결제 가능한 상태에서만 호출합니다. 가상계좌 잔액에서 차감하고 원장에 기록합니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<SettlementResponse>> pay(
            @PathVariable Long settlementId,
            @Valid @RequestBody SettlementPayRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 상태 확인 -> 결제수단 소유 확인 -> 원장 기록 -> 상태 PAID -> 프로젝트 결제 상태 갱신
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_PAID", "결제가 완료되었습니다.", sampleSettlement()));
    }

    @GetMapping("/penalties/mine")
    @Operation(summary = "내 위약금 목록", description = "계약 파기로 발생한 위약금 내역입니다.")
    public ResponseEntity<ApiResponse<List<PenaltyResponse>>> findMyPenalties(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내가 납부자인 위약금 조회
        return ResponseEntity.ok(ApiResponse.success("PENALTIES_FOUND", "조회에 성공했습니다.",
                List.of(samplePenalty())));
    }

    @PostMapping("/penalties/{penaltyId}/payment")
    @Operation(summary = "위약금 납부")
    public ResponseEntity<ApiResponse<PenaltyResponse>> payPenalty(
            @PathVariable Long penaltyId,
            @Valid @RequestBody SettlementPayRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 결제수단 소유 확인 -> 원장 기록 -> 상태 PAID
        return ResponseEntity.ok(ApiResponse.success("PENALTY_PAID", "납부가 완료되었습니다.", samplePenalty()));
    }

    // ==========================================
    // 관리자 (R39)
    // ==========================================

    @GetMapping("/admin/summary")
    @Operation(summary = "[관리자] 정산 집계", description = "총 수익, 이번 달 수익, 결제 예정·미납·실패, 위약금 수수료입니다.")
    public ResponseEntity<ApiResponse<SettlementSummaryResponse>> findSummaryForAdmin() {
        // TODO: 집계 쿼리
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENT_SUMMARY_FOUND", "조회에 성공했습니다.",
                new SettlementSummaryResponse(1_105_000L, 400_000L, 225_000L, 150_000L, 0L, 0L)));
    }

    @GetMapping("/admin")
    @Operation(summary = "[관리자] 정산 목록",
            description = "정산번호 검색, 납부자 구분·단계·상태 필터를 지원합니다.")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> findAllForAdmin(
            @RequestParam(required = false) String settlementNo,
            @RequestParam(required = false) PartyRole payerRole,
            @RequestParam(required = false) SettlementPhase phase,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        // TODO: 전체 정산 검색
        return ResponseEntity.ok(ApiResponse.success("SETTLEMENTS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleSettlement()), page, size, 1, 1, true, true)));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private SettlementResponse sampleSettlement() {
        return new SettlementResponse(700L, "ST-2026-000045", 1L, "페어링 웹 리뉴얼", 600L,
                PartyRole.CLIENT, "주식회사 페어링", SettlementPhase.DEPOSIT, 22_000_000L,
                new BigDecimal("3.00"), new BigDecimal("0.00"), 660_000L,
                SettlementStatus.PENDING,
                "신한카드 **** 1234", "AP-20260804-3821", null, null,
                LocalDate.now().plusDays(7), true, null);
    }

    private PenaltyResponse samplePenalty() {
        return new PenaltyResponse(800L, "페어링 웹 리뉴얼", 600L, PartyRole.CLIENT, "PLATFORM",
                10_000_000L, new BigDecimal("10.00"), 1_000_000L, PenaltyStatus.PENDING, null);
    }
}
