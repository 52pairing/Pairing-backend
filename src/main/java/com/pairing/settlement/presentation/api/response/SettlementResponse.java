package com.pairing.settlement.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
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

        /*
         * 발주 기업명. payerName 과 다르다 — 그쪽은 "이 수수료를 낸 사람"이라 프리랜서가
         * 조회하면 자기 이름이 나온다. 결제 내역 화면은 "어느 회사 프로젝트였나"를 보여줘야 해서
         * 상대 쪽 이름이 따로 필요하다. 프로젝트가 지워졌으면 null 이다.
         */
        @Schema(description = "발주 기업명. 프로젝트가 삭제됐으면 null", example = "주식회사 오이랩")
        String clientName,
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

        @Schema(description = "납부 기한") LocalDate dueDate,
        @Schema(description = "결제 가능 여부. false 면 결제 버튼을 비활성화한다.", example = "true") boolean payable,
        @Schema(description = "완료 시각") LocalDateTime paidAt
) {

    /**
     * 정산 + 다른 도메인 값 -> 응답.
     *
     * <p>projectTitle 은 project 도메인, payerName 은 account 도메인 값이라 조립하는 쪽이 넘긴다.
     * 정산 조회 서비스가 직접 읽으면 project -> settlement 방향과 맞물려 순환이 된다.
     *
     * <p>paymentMethodLabel 도 account 도메인 값이다. 미결제 정산이나 결제 후 삭제된 수단이면
     * null 로 흘린다. 결제일시·승인번호는 그대로 남으므로 화면이 빈 칸만 감추면 된다.
     */
    public static SettlementResponse from(SettlementResult result, String projectTitle, String payerName,
                                          String clientName, String paymentMethodLabel) {
        return new SettlementResponse(
                result.settlementId(),
                result.settlementNo(),
                result.projectId(),
                projectTitle,
                result.contractId(),
                result.payerRole(),
                payerName,
                clientName,
                result.phase(),
                result.baseAmount(),
                result.feeRate(),
                result.gradeDiscount(),
                result.feeAmount(),
                result.status(),
                paymentMethodLabel,
                result.approvalNo(),
                result.failReason(),
                result.overdueReason(),
                result.dueDate(),
                result.payable(),
                result.paidAt());
    }
}
