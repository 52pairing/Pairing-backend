package com.pairing.project.presentation.api.response;

import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.model.ProjectTab;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 탭 옆에 붙는 건수 배지.
 *
 * <p>클라이언트 화면은 탭이 상태를 묶어서 {@code tab} 이 채워지고, 관리자 화면은 상태 단위라
 * {@code status} 가 채워진다. 둘 중 하나만 값이 있다.
 */
@Schema(description = "프로젝트 탭 건수")
public record ProjectTabCountResponse(

        @Schema(description = "탭. 관리자 응답에서는 null", example = "MATCHING") ProjectTab tab,
        @Schema(description = "상태. 클라이언트 응답에서는 null", example = "RECRUITING") ProjectStatus status,
        @Schema(description = "탭 라벨", example = "매칭중") String label,
        @Schema(description = "건수", example = "3") long count
) {
}
