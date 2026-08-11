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

        // "재추천을 반복하면 true"가 아니다. 대기 순번(노출 인원 밖) 후보 중 50점 미만이 있으면 켜진다.
        // 지금 노출된 후보의 점수는 보지 않는다 — 이 경고는 "지금 후보가 별로다"가 아니라
        // "더 눌러봐야 소용없다"를 알리는 값이라서다(프론트 문구도 "다음 순번 후보 중 적합도가
        // 낮은 후보가 포함될 수 있어요"로 확정돼 있다). 조건을 넓히면 그 문구가 거짓말이 된다.
        @Schema(description = "적합도 저하 경고. 대기 순번(노출 인원 밖) 후보 중 50점 미만이 있으면 true. "
                + "지금 노출된 후보의 점수는 보지 않는다.", example = "false")
        boolean lowScoreWarned,

        @Schema(description = "후보 목록. 조건에 맞는 후보가 부족하면 모집 인원보다 적을 수 있다.")
        List<CandidateResponse> candidates
) {
}
