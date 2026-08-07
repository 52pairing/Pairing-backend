package com.pairing.project.presentation.api.request;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 등록. (요구사항 R30)
 *
 * <p>화면 4개로 나뉘어 있지만 최종 제출 한 번으로 등록한다. 화면 간 임시 저장은 프론트가 맡는다.
 * 파일은 먼저 {@code POST /api/v1/files} 로 올리고 fileId 만 담는다.
 */
@Schema(description = "프로젝트 등록 요청")
public record ProjectCreateRequest(

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        @NotBlank(message = "프로젝트명은 필수입니다.")
        @Size(max = 200, message = "프로젝트명은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "모집 인원(포지션) 목록. 최소 1건", minLength = 1)
        @NotEmpty(message = "필요 인력은 최소 1건입니다.")
        @Valid
        List<PositionRequest> positions,

        @Schema(description = "시작 희망일", example = "2026-09-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDesiredDate,

        @Schema(description = "시작일 협의 가능 여부. 희망일과 함께 보낼 수 있다.", example = "true")
        boolean startNegotiable,

        @Schema(description = "예상 기간 값. 최대 24(개월/주)", example = "6")
        @Min(value = 1, message = "기간은 1 이상입니다.")
        @Max(value = 24, message = "기간은 최대 24입니다.")
        int periodValue,

        @Schema(description = "기간 단위", example = "MONTH")
        @NotNull(message = "기간 단위는 필수입니다.")
        PeriodUnit periodUnit,

        @Schema(description = "프로젝트 예산(원, 부가세 별도). 500만~10억", example = "50000000")
        @NotNull(message = "예산은 필수입니다.")
        @Min(value = 5_000_000L, message = "예산은 최소 500만원입니다.")
        @Max(value = 1_000_000_000L, message = "예산은 최대 10억원입니다.")
        Long budgetAmount,

        @Schema(description = "근무 방식", example = "REMOTE")
        @NotNull(message = "근무 방식은 필수입니다.")
        WorkStyle workStyle,

        @Schema(description = "근무 형태", example = "FULL_TIME")
        @NotNull(message = "근무 형태는 필수입니다.")
        WorkForm workForm,

        @Schema(description = "근무 장소. 상주일 때 사용", example = "서울 강남구")
        @Size(max = 255)
        String workLocation,

        @Schema(description = "현재 프로젝트 진행 상황")
        @NotBlank(message = "진행 상황은 필수입니다.")
        @Size(max = 1500, message = "1500자 이하여야 합니다.")
        String currentSituation,

        @Schema(description = "주요 담당 업무")
        @NotBlank(message = "담당 업무는 필수입니다.")
        @Size(max = 1500, message = "1500자 이하여야 합니다.")
        String mainTask,

        @Schema(description = "세부 업무범위")
        @Size(max = 1500, message = "1500자 이하여야 합니다.")
        String detailScope,

        @Schema(description = "기타 전달사항 또는 우대사항")
        @Size(max = 1500, message = "1500자 이하여야 합니다.")
        String extraNote,

        @Schema(description = "첨부 자료 fileId 목록. 최대 10개", example = "[1, 2]")
        @Size(max = 10, message = "자료는 10개까지 등록할 수 있습니다.")
        List<Long> fileIds,

        @Schema(description = "등록 전 안내 동의. 동의해야 등록된다.", example = "true")
        @AssertTrue(message = "등록 전 안내에 동의해야 합니다.")
        boolean noticeAgreed
) {
}
