package com.pairing.contract.presentation.api.response;

import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준계약서 상세. (요구사항 R43)
 *
 * <p>협상에서 합의된 금액·기간·업무 범위·근무 조건이 그대로 반영된다.
 *
 * <p>서명 기한은 두지 않는다. 요구사항 44행이 "계약서 생성 후 서명 기한은 무기한" 이다.
 */
@Schema(description = "계약 상세 응답")
public record ContractResponse(

        @Schema(description = "계약 ID", example = "600") Long contractId,
        @Schema(description = "계약번호", example = "PR-2026-000123") String contractNo,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "협상 ID", example = "300") Long negotiationId,
        @Schema(description = "클라이언트명", example = "주식회사 페어링") String clientName,
        @Schema(description = "프리랜서명", example = "홍길동") String freelancerName,
        @Schema(description = "계약 대상 직무") JobRole jobRole,
        @Schema(description = "상태") ContractStatus status,

        @Schema(description = "계약 총액(원)", example = "22000000") Long totalAmount,
        @Schema(description = "급여 단위. 화면에 \"월 6,000,000원\" 으로 찍는다") PayUnit payUnit,
        @Schema(description = "단위당 급여(원)", example = "6000000") Long payAmount,
        @Schema(description = "착수금(원)", example = "6600000") Long downAmount,
        @Schema(description = "잔금(원)", example = "15400000") Long finalAmount,
        @Schema(description = "계약 시작일") LocalDate startDate,
        @Schema(description = "계약 종료일") LocalDate endDate,
        @Schema(description = "근무 방식") WorkStyle workStyle,
        @Schema(description = "근무 형태") WorkForm workForm,
        @Schema(description = "근무 장소") String workLocation,

        @Schema(description = "검수 기간(일)", example = "7") int inspectionDays,
        @Schema(description = "대금 지급 기한(일)", example = "7") int paymentDays,
        @Schema(description = "비밀유지 기간(년)", example = "3") int confidentialYears,
        @Schema(description = "위약금 비율(%)", example = "10.00") java.math.BigDecimal penaltyRate,
        @Schema(description = "특약사항. 협상 로그를 근거로 작성된다.") String specialTerms,

        @Schema(description = "계약서 조항 본문. 화면에 순서대로 나열한다") List<Clause> clauses,
        @Schema(description = "계약서 PDF fileId. 생성 전에는 null", example = "9") Long pdfFileId,
        @Schema(description = "당사자별 서명 현황") List<Signature> signatures,
        @Schema(description = "체결 시각") LocalDateTime signedAt,
        @Schema(description = "생성 시각") LocalDateTime createdAt
) {

    /**
     * 상세 응답 조립.
     *
     * <p>급여 단위는 월로 고정한다. 협상이 합의해 넘겨주는 값이 월 단가 하나뿐이다.
     *
     * <p>{@code clauses} 는 아직 빈 배열이다. 조항 본문 생성은 계약서 렌더링 작업에서 붙인다.
     * 그때 {@code content_json} 에 굳혀 상세 응답과 PDF 가 같은 문장을 쓰게 한다.
     */
    public static ContractResponse from(ContractDetail detail) {
        Contract contract = detail.contract();

        List<Signature> signatures = contract.getSignatures().stream()
                .map(s -> new Signature(
                        s.getPartyRole(),
                        s.getPartyRole() == PartyRole.CLIENT
                                ? detail.clientName() : detail.freelancerName(),
                        s.getStatus(),
                        s.getSignedAt(),
                        s.getRejectReason()))
                .toList();

        return new ContractResponse(
                contract.getId(),
                contract.getContractNo(),
                contract.getProjectId(),
                detail.projectTitle(),
                contract.getNegotiationId(),
                detail.clientName(),
                detail.freelancerName(),
                detail.jobRole(),
                contract.getStatus(),
                contract.getTotalAmount(),
                PayUnit.MONTHLY,
                contract.getSalaryAmount(),
                contract.getDownAmount(),
                contract.getFinalAmount(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getWorkStyle(),
                contract.getWorkForm(),
                contract.getWorkLocation(),
                contract.getInspectionDays(),
                contract.getPaymentDays(),
                contract.getConfidentialYears(),
                contract.getPenaltyRate(),
                contract.getSpecialTerms(),
                List.of(),
                contract.getPdfFileId(),
                signatures,
                contract.getSignedAt(),
                contract.getCreatedAt());
    }

    @Schema(description = "계약서 조항")
    public record Clause(
            @Schema(description = "조 번호", example = "1") int no,
            @Schema(description = "제목", example = "용역의 내용") String title,
            @Schema(description = "본문") String content
    ) {
    }

    @Schema(description = "서명 현황")
    public record Signature(
            @Schema(description = "당사자 구분") PartyRole partyRole,
            @Schema(description = "이름", example = "홍길동") String name,
            @Schema(description = "서명 상태") SignatureStatus status,
            @Schema(description = "서명 시각") LocalDateTime signedAt,
            @Schema(description = "거부 사유") String rejectReason
    ) {
    }
}
