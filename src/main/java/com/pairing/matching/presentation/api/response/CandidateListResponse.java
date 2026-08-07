package com.pairing.matching.presentation.api.response;

import com.pairing.matching.domain.model.RecommendationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 포지션 단위 추천 결과. */
@Schema(description = "추천 후보 목록 응답")
public record CandidateListResponse(

        @Schema(description = "포지션 ID", example = "10")
        Long positionId,

        @Schema(description = "추천 라운드 ID", example = "5")
        Long roundId,

        @Schema(description = "라운드 번호", example = "1")
        int roundNo,

        @Schema(description = "라운드 종류")
        RecommendationType roundType,

        @Schema(description = "모집 인원", example = "2")
        int headcount,

        @Schema(description = "무료 재추천 사용 가능 여부. 요청 후보 전원 거절 시 1회", example = "false")
        boolean freeRerecommendAvailable,

        @Schema(description = "남은 유료 재추천 횟수", example = "5")
        int paidRerecommendRemaining,

        @Schema(description = "적합도 저하 경고. 재추천을 반복하면 true 가 된다.", example = "false")
        boolean lowScoreWarned,

        @Schema(description = "후보 목록. 조건에 맞는 후보가 부족하면 모집 인원보다 적을 수 있다.")
        List<CandidateResponse> candidates
) {
}
