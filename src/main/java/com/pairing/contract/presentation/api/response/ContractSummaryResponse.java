package com.pairing.contract.presentation.api.response;

import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.domain.model.Contract;
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

    /**
     * 목록 카드 한 장.
     *
     * <p>급여 단위는 월로 고정한다. 협상이 합의해 넘겨주는 값이 월 단가 하나뿐이다.
     */
    public static ContractSummaryResponse from(ContractSummary summary) {
        Contract contract = summary.contract();

        return new ContractSummaryResponse(
                contract.getId(),
                contract.getContractNo(),
                summary.projectTitle(),
                summary.counterpartName(),
                contract.getStatus(),
                contract.getTotalAmount(),
                contract.getStartDate(),
                contract.getEndDate(),
                summary.signatureRequired(),
                PayUnit.MONTHLY,
                contract.getSalaryAmount());
    }
}
