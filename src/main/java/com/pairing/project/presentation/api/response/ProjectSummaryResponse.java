package com.pairing.project.presentation.api.response;

import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 목록용 요약. 카드 한 장에 필요한 값만 담는다. */
@Schema(description = "프로젝트 목록 항목")
public record ProjectSummaryResponse(

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "화면 표시용 프로젝트 번호", example = "PRJ-001")
        String projectNo,

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        String title,

        @Schema(description = "프로젝트 상태", example = "RECRUITING")
        ProjectStatus status,

        @Schema(description = "상태 보조 문구", example = "2 / 3명 확정 · 1명 추가 모집 필요")
        String statusNote,

        @Schema(description = "결제 상태", example = "DEPOSIT_PAID")
        ProjectPaymentStatus paymentStatus,

        // 카드 상단 칩. 직무는 라벨(한글), 스킬은 코드가 아니라 표기명으로 내려준다.
        @Schema(description = "모집 직무 라벨", example = "[\"풀스택 개발자\"]")
        List<String> jobRoleLabels,

        @Schema(description = "요구 스킬 라벨", example = "[\"React\", \"Node.js\"]")
        List<String> skillLabels,

        @Schema(description = "예산(원)", example = "50000000")
        Long budgetAmount,

        @Schema(description = "예상 기간", example = "3개월")
        String periodLabel,

        @Schema(description = "시작 희망일")
        LocalDate startDesiredDate,

        @Schema(description = "총 모집 인원", example = "3")
        int totalHeadcount,

        @Schema(description = "확정 인원", example = "2")
        int confirmedHeadcount,

        @Schema(description = "모집 마감 시각")
        LocalDateTime recruitDeadline,

        // 아래 둘은 관리자 목록 전용이다. 내 프로젝트 목록에서는 null 로 내려간다.
        @Schema(description = "클라이언트명", example = "삼성전자")
        String clientName,

        @Schema(description = "매칭된 프리랜서명. 없으면 null", example = "김프리")
        String matchedFreelancerName,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt
) {
}
