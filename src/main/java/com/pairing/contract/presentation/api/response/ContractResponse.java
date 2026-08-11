package com.pairing.contract.presentation.api.response;

import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.global.infrastructure.s3.CdnMappable;
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
        @Schema(description = "계약서 머리말의 갑 표시") ClientParty client,
        @Schema(description = "계약서 머리말의 을 표시") FreelancerParty freelancer,
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
     * <p>{@code clauses} 는 조회 시점에 렌더링된 계약서 본문이다. PDF 도 같은 렌더러를 쓰므로
     * 화면과 파일의 문장이 갈리지 않는다.
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
                        detail.signatureImageUrls().get(s.getAccountId()),
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
                new ClientParty(detail.client().companyName(), detail.client().businessNo(),
                        detail.client().representative(), detail.client().address(),
                        detail.client().phone()),
                new FreelancerParty(detail.freelancer().name(), detail.freelancer().phone(),
                        detail.jobRole(), detail.freelancer().settlementAccount()),
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
                detail.clauses().stream()
                        .map(c -> new Clause(c.no(), c.title(), c.content()))
                        .toList(),
                contract.getPdfFileId(),
                signatures,
                contract.getSignedAt(),
                contract.getCreatedAt());
    }

    @Schema(description = "계약서 머리말의 갑 표시")
    public record ClientParty(
            @Schema(description = "기업명", example = "카카오 주식회사") String companyName,
            @Schema(description = "사업자등록번호", example = "1208147521") String businessNo,
            @Schema(description = "대표자", example = "정신아") String representative,
            @Schema(description = "주소") String address,
            @Schema(description = "연락처", example = "0212345678") String phone
    ) {
    }

    /** 정산 계좌는 조회 시점 값이다. 계약 테이블에 계좌 칸이 없어 체결 시점으로 동결하지 못한다. */
    @Schema(description = "계약서 머리말의 을 표시")
    public record FreelancerParty(
            @Schema(description = "성명", example = "김민준") String name,
            @Schema(description = "연락처", example = "01098765432") String phone,
            @Schema(description = "직군", example = "BACKEND") JobRole jobRole,
            @Schema(description = "정산 계좌", example = "카카오뱅크 3333012345678 (예금주: 김민준)")
            String settlementAccount
    ) {
    }

    @Schema(description = "계약서 조항")
    public record Clause(
            @Schema(description = "조 번호", example = "1") int no,
            @Schema(description = "제목", example = "용역의 내용") String title,
            @Schema(description = "본문") String content
    ) {
    }

    /**
     * {@code signatureImageUrl} 은 화면에서 그린 서명 그림이다. 안 그리고 동의만 했으면 null.
     *
     * <p>DB 에는 object key 만 있고 {@link CdnMappable} 이라 직렬화 시점에 절대 URL 로 바뀐다.
     */
    @Schema(description = "서명 현황")
    public record Signature(
            @Schema(description = "당사자 구분") PartyRole partyRole,
            @Schema(description = "이름", example = "홍길동") String name,
            @Schema(description = "서명 상태") SignatureStatus status,
            @Schema(description = "서명 시각") LocalDateTime signedAt,
            @Schema(description = "서명 이미지 주소. 없으면 null") String signatureImageUrl,
            @Schema(description = "거부 사유") String rejectReason
    ) implements CdnMappable {
    }
}
