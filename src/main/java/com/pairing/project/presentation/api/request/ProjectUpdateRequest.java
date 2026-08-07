package com.pairing.project.presentation.api.request;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
 * 프로젝트 수정. (요구사항 R32)
 *
 * <p>등록 시 입력한 값은 모두 수정할 수 있다. 다만 이미 진행 중인 매칭에는
 * 매칭 시작 시점의 스냅샷이 적용되고 수정 내용은 반영되지 않는다.
 */
@Schema(description = "프로젝트 수정 요청")
public record ProjectUpdateRequest(

        @Schema(description = "프로젝트명")
        @NotBlank(message = "프로젝트명은 필수입니다.")
        @Size(max = 200)
        String title,

        @Schema(description = "모집 인원(포지션) 목록")
        @NotEmpty(message = "필요 인력은 최소 1건입니다.")
        @Size(max = 100, message = "모집 직군은 최대 100건입니다.")
        @Valid
        List<PositionUpdateRequest> positions,

        @Schema(description = "시작 희망일")
        @NotNull(message = "시작 희망일은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDesiredDate,

        @Schema(description = "시작일 협의 가능 여부")
        boolean startNegotiable,

        @Schema(description = "예상 기간 값")
        @Min(1) @Max(24)
        int periodValue,

        @Schema(description = "기간 단위")
        @NotNull(message = "기간 단위는 필수입니다.")
        PeriodUnit periodUnit,

        @Schema(description = "프로젝트 예산(원, 부가세 별도)")
        @NotNull(message = "예산은 필수입니다.")
        @Min(5_000_000L) @Max(1_000_000_000L)
        Long budgetAmount,

        @Schema(description = "근무 방식")
        @NotNull(message = "근무 방식은 필수입니다.")
        WorkStyle workStyle,

        @Schema(description = "근무 형태")
        @NotNull(message = "근무 형태는 필수입니다.")
        WorkForm workForm,

        @Schema(description = "현재 프로젝트 진행 상황")
        @NotBlank @Size(max = 1500)
        String currentSituation,

        @Schema(description = "주요 담당 업무")
        @NotBlank @Size(max = 1500)
        String mainTask,

        @Schema(description = "세부 업무범위")
        @Size(max = 1500)
        String detailScope,

        @Schema(description = "기타 전달사항")
        @Size(max = 1500)
        String extraNote,

        @Schema(description = "첨부 자료 fileId 목록")
        @Size(max = 10)
        List<Long> fileIds
) {
}
