package com.pairing.contract.presentation.api.response;

import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.meta.domain.model.PayUnit;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 계약 목록 항목. 헤더의 '계약관리'에서 쓴다. */
@Schema(description = "계약 목록 항목")
public record ContractSummaryResponse(

        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "계약번호", example = "PR-2026-000123") String contractNo,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "상대 이름", example = "홍길동") String counterpartName,
        @Schema(description = "상태") ContractStatus status,
        @Schema(description = "계약 총액(원)", example = "22000000") Long totalAmount,
        @Schema(description = "계약 시작일") LocalDate startDate,
        @Schema(description = "계약 종료일") LocalDate endDate,
        @Schema(description = "내 서명이 필요한지", example = "true") boolean signatureRequired,
        @Schema(description = "급여 단위") PayUnit payUnit,
        @Schema(description = "단위당 급여(원)", example = "6200000") Long payAmount
) {
}
