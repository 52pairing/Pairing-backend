package com.pairing.contract.presentation.api.response;

import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.account.domain.model.BusinessField;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 계약 목록 항목. 헤더의 '계약관리'에서 쓴다. */
@Schema(description = "계약 목록 항목")
public record ContractSummaryResponse(

        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "계약번호", example = "PR-2026-000123") String contractNo,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "계약 대상 직무. 카드에 \"김개발 · 프론트엔드\" 로 찍는다") JobRole jobRole,
        @Schema(description = "상대 이름", example = "홍길동") String counterpartName,

        @Schema(description = "갑의 업종. 프리랜서 화면 카드가 \"주식회사 페어링 · IT/컨텐츠/AI\" 로 "
                + "찍는다. 클라이언트가 조회하면 상대가 프리랜서라 null 이다.",
                example = "IT_CONTENTS_AI")
        BusinessField clientBusinessField,

        @Schema(description = "상태") ContractStatus status,
        @Schema(description = "계약 총액(원)", example = "22000000") Long totalAmount,
        @Schema(description = "계약 시작일") LocalDate startDate,
        @Schema(description = "계약 종료일") LocalDate endDate,
        @Schema(description = "내 서명이 필요한지", example = "true") boolean signatureRequired,

        @Schema(description = "클라이언트(갑) 서명 완료 여부", example = "false") boolean clientSigned,
        @Schema(description = "프리랜서(을) 서명 완료 여부", example = "true") boolean freelancerSigned,

        @Schema(description = "프리랜서 착수금 수수료 결제 완료 여부. 정산 값이다. "
                + "수수료는 계약 체결 시점에 생기므로 체결 전에는 항상 false 다. "
                + "\"결제 필요\" 배지는 status 가 SIGNED 이고 이 값이 false 일 때만 띄운다.",
                example = "false")
        boolean depositPaid,

        @Schema(description = "지금 내가 결제할 정산 ID. 낼 게 없으면 null. "
                + "카드의 수수료 결제 버튼이 이 값으로 POST /settlements/{id}/payment 를 부른다. "
                + "계약에 걸린 정산은 프리랜서 몫이라 클라이언트가 조회하면 항상 null 이다.",
                example = "700")
        Long payableSettlementId,

        @Schema(description = "근무 방식") WorkStyle workStyle,
        @Schema(description = "근무 형태") WorkForm workForm,
        @Schema(description = "계약서 생성 시각") LocalDateTime createdAt,

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
                contract.getProjectId(),
                summary.projectTitle(),
                summary.jobRole(),
                summary.counterpartName(),
                summary.clientBusinessField(),
                contract.getStatus(),
                contract.getTotalAmount(),
                contract.getStartDate(),
                contract.getEndDate(),
                summary.signatureRequired(),
                summary.clientSigned(),
                summary.freelancerSigned(),
                summary.depositPaid(),
                summary.payableSettlementId(),
                contract.getWorkStyle(),
                contract.getWorkForm(),
                contract.getCreatedAt(),
                PayUnit.MONTHLY,
                contract.getSalaryAmount());
    }
}
