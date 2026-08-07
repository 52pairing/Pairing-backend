package com.pairing.settlement.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.global.common.api.response.StatusHistoryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 수수료 정산 1건.
 *
 * <p>수수료율은 계약 금액 구간(1억 기준)과 당사자 구분에 따라 달라진다.
 */
@Schema(description = "정산 응답")
public record SettlementResponse(

        @Schema(description = "정산 ID", example = "700") Long settlementId,
        @Schema(description = "정산번호", example = "ST-2026-000045") String settlementNo,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "납부자 구분") PartyRole payerRole,
        @Schema(description = "납부자 이름", example = "주식회사 페어링") String payerName,
        @Schema(description = "수수료 단계") SettlementPhase phase,
        @Schema(description = "기준 금액(원). 계약 금액", example = "22000000") Long baseAmount,
        @Schema(description = "수수료율(%)", example = "3.00") BigDecimal feeRate,
        @Schema(description = "등급 할인율(%)", example = "0.00") BigDecimal gradeDiscount,
        @Schema(description = "수수료(원)", example = "660000") Long feeAmount,
        @Schema(description = "상태") SettlementStatus status,
        @Schema(description = "결제수단 표기. 미결제면 null", example = "신한카드 **** 1234")
        String paymentMethodLabel,

        @Schema(description = "결제 승인번호. 미결제면 null", example = "AP-20260804-3821")
        String approvalNo,

        @Schema(description = "결제 실패 사유") String failReason,
        @Schema(description = "미납 사유") String overdueReason,

        @Schema(description = "상태 이력. 관리자 상세에서만 채워진다")
        List<StatusHistoryResponse> statusHistories,

        @Schema(description = "납부 기한") LocalDate dueDate,
        @Schema(description = "결제 가능 여부. false 면 결제 버튼을 비활성화한다.", example = "true") boolean payable,
        @Schema(description = "완료 시각") LocalDateTime paidAt
) {
}
