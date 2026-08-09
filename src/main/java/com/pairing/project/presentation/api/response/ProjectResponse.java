package com.pairing.project.presentation.api.response;

import com.pairing.global.infrastructure.s3.CdnMappable;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionStatus;
import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 프로젝트 상세. */
@Schema(description = "프로젝트 상세 응답")
public record ProjectResponse(

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        String title,

        @Schema(description = "프로젝트 상태", example = "RECRUITING")
        ProjectStatus status,

        @Schema(description = "상태 보조 문구. 인원별 현황을 사람이 읽는 문장으로 준다.",
                example = "2 / 3명 협상중 · 1명 모집중")
        String statusNote,

        @Schema(description = "결제 상태", example = "DEPOSIT_PAID")
        ProjectPaymentStatus paymentStatus,

        @Schema(description = "시작 희망일")
        LocalDate startDesiredDate,

        @Schema(description = "시작일 협의 가능 여부")
        boolean startNegotiable,

        @Schema(description = "예상 기간 값", example = "6")
        int periodValue,

        @Schema(description = "기간 단위", example = "MONTH")
        PeriodUnit periodUnit,

        @Schema(description = "예산(원, 부가세 별도)", example = "50000000")
        Long budgetAmount,

        @Schema(description = "근무 방식")
        WorkStyle workStyle,

        @Schema(description = "근무 형태")
        WorkForm workForm,

        @Schema(description = "근무 장소")
        String workLocation,

        @Schema(description = "현재 진행 상황")
        String currentSituation,

        @Schema(description = "주요 담당 업무")
        String mainTask,

        @Schema(description = "세부 업무범위")
        String detailScope,

        @Schema(description = "기타 전달사항")
        String extraNote,

        @Schema(description = "총 모집 인원", example = "3")
        int totalHeadcount,

        @Schema(description = "확정 인원", example = "2")
        int confirmedHeadcount,

        @Schema(description = "모집 마감 시각. 기본 2주, 1주씩 최대 2회 연장")
        LocalDateTime recruitDeadline,

        @Schema(description = "연장 횟수", example = "0")
        int extensionCount,

        @Schema(description = "사용한 무료 재추천 횟수(최대 1)", example = "0")
        int freeRerecommendUsed,

        @Schema(description = "사용한 유료 재추천 횟수(최대 5)", example = "0")
        int paidRerecommendUsed,

        @Schema(description = "포지션 목록")
        List<Position> positions,

        @Schema(description = "프리랜서 현황. 상세 화면 하단 목록")
        List<ParticipantFreelancer> freelancers,

        @Schema(description = "첨부 자료")
        List<AttachedFile> files,

        @Schema(description = "등록 시각")
        LocalDateTime createdAt,

        @Schema(description = "지금 결제해야 할 정산 ID. 결제할 게 없으면 null. "
                + "등록 완료면 착수금, 완료 대기면 성공보수를 가리킨다.", example = "700")
        Long payableSettlementId,

        @Schema(description = "중도 종료 시 위약금 안내 대상 여부. 계약을 맺은 인원이 있으면 true. "
                + "종료 확인 화면에 '위약금이 발생할 수 있습니다' 문구를 띄우는 데 쓴다.", example = "false")
        boolean penaltyExpected
) {

    /**
     * 도메인 + 첨부 메타 -> 응답.
     *
     * <p>statusNote / freelancers / payableSettlementId 는 매칭·정산 도메인 값이라 아직 비운다.
     */
    public static ProjectResponse from(com.pairing.project.application.result.ProjectDetail detail) {
        com.pairing.project.domain.model.Project p = detail.project();

        List<Position> positions = p.getPositions().stream()
                .map(pos -> new Position(
                        pos.getId(), pos.getPositionNo(), pos.getJobCategory(), pos.getJobRole(),
                        pos.getMinCareerYears(), pos.getHeadcount(), pos.getConfirmedCount(),
                        pos.getStatus(), pos.getSkills()))
                .toList();

        // objectKey 를 fileUrl 에 그대로 담는다. CdnMappable 이라 직렬화 시점에 절대 URL 로 바뀐다.
        List<AttachedFile> files = detail.files().stream()
                .map(f -> new AttachedFile(f.fileId(), f.originalName(), f.sizeBytes(), f.objectKey()))
                .toList();

        return new ProjectResponse(
                p.getId(), p.getTitle(), p.getStatus(), null,
                p.getPaymentStatus(), p.getStartDesiredDate(), p.isStartNegotiable(),
                p.getPeriodValue(), p.getPeriodUnit(), p.getBudgetAmount(),
                p.getWorkStyle(), p.getWorkForm(), p.getWorkLocation(),
                p.getCurrentSituation(), p.getMainTask(), p.getDetailScope(), p.getExtraNote(),
                p.getTotalHeadcount(), p.getConfirmedHeadcount(),
                p.getRecruitDeadline(), p.getExtensionCount(),
                p.getFreeRerecommendUsed(), p.getPaidRerecommendUsed(),
                positions, List.of(), files,
                p.getCreatedAt(), detail.payableSettlementId(), p.hasConfirmedMember());
    }

    @Schema(description = "포지션")
    public record Position(
            @Schema(description = "포지션 ID", example = "10") Long positionId,
            @Schema(description = "포지션 번호", example = "1") int positionNo,
            @Schema(description = "직군") JobCategory jobCategory,
            @Schema(description = "직무") JobRole jobRole,
            @Schema(description = "최소 경력(년)", example = "3") int minCareerYears,
            @Schema(description = "모집 인원", example = "2") int headcount,
            @Schema(description = "확정 인원", example = "1") int confirmedCount,
            @Schema(description = "포지션 상태") PositionStatus status,
            @Schema(description = "요구 스킬") List<SkillCode> skills
    ) {
    }

    /** 상세 화면의 프리랜서 현황 한 줄. 상태에 따라 화면이 보여줄 버튼이 달라진다. */
    @Schema(description = "참여 프리랜서")
    public record ParticipantFreelancer(
            @Schema(description = "프리랜서 계정 ID", example = "7") Long freelancerId,
            @Schema(description = "이름", example = "김개발") String name,
            @Schema(description = "직무") JobRole jobRole,
            @Schema(description = "매칭 상태") MatchingStatus status,
            @Schema(description = "급여 단위") PayUnit payUnit,
            @Schema(description = "확정 급여(원)", example = "6200000") Long payAmount,
            @Schema(description = "상태 라벨", example = "협상중") String statusLabel,
            @Schema(description = "협상 ID. 협상 이전이면 null", example = "500") Long negotiationId,
            @Schema(description = "계약 ID. 계약 이전이면 null", example = "600") Long contractId
    ) {
    }

    @Schema(description = "첨부 자료")
    public record AttachedFile(
            @Schema(description = "파일 ID", example = "1") Long fileId,
            @Schema(description = "원본 파일명", example = "기획서.pdf") String originalName,
            @Schema(description = "크기(byte)", example = "29491") Long sizeBytes,
            @Schema(description = "다운로드 URL") String fileUrl
    ) implements CdnMappable {
    }
}
