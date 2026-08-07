package com.pairing.project.presentation.api.response;

import com.pairing.meta.domain.model.JobRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 사전 검수 결과. (요구사항 R28, R30 / 프로젝트 등록 5단계)
 *
 * <p>현재 프리랜서 풀 기준의 예상치다. 실제 후보 수와 매칭 성사 여부는 달라질 수 있다.
 * 한 직무라도 {@code matchable} 이 false 면 화면에서 조건 조정을 안내하지만, 그대로 등록할 수도 있다.
 */
@Schema(description = "사전 검수 응답")
public record ProjectPreReviewResponse(

        @Schema(description = "전 포지션이 매칭 가능한지", example = "true")
        boolean allMatchable,

        @Schema(description = "포지션별 검수 결과. 요청 positions 와 같은 순서, 같은 개수")
        List<Item> items,

        @Schema(description = "안내 문구",
                example = "현재 프리랜서 풀 기준 예상 결과입니다. 실제 후보 수 및 매칭 성사 여부는 달라질 수 있습니다.")
        String notice
) {

    @Schema(name = "PreReviewItem", description = "포지션별 검수 결과")
    public record Item(

            @Schema(description = "요청 positions 배열의 순서(0-based). 화면 카드 매핑용", example = "0")
            int positionIndex,

            @Schema(description = "직무") JobRole jobRole,

            @Schema(description = "모집 인원", example = "2") int headcount,

            @Schema(description = "예상 후보 수", example = "5") int expectedCandidateCount,

            @Schema(description = "매칭 가능 여부", example = "true") boolean matchable,

            @Schema(description = "불가 사유. matchable 이 true 면 null",
                    example = "현재 조건에 맞는 백엔드 개발자 후보가 모집 인원보다 부족합니다.")
            String message,

            // 화면에 불릿으로 그대로 찍는다. matchable 이 true 면 빈 배열.
            @Schema(description = "조건 조정 제안", example = "[\"요구 스킬을 변경해보세요.\"]")
            List<String> suggestions
    ) {
    }
}
