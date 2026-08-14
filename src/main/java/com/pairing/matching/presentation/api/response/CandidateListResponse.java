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

        @Schema(description = "예산 조합 경고. 노출 후보들의 희망 단가 합계가 남은 예산을 넘으면 true.", example = "false")
        boolean budgetWarned,

        @Schema(description = "후보 목록. 조건에 맞는 후보가 부족하면 모집 인원보다 적을 수 있다.")
        List<CandidateResponse> candidates,

        @Schema(description = "AI가 아직 후보를 고르는 중인지. true면 잠시 뒤 다시 조회하면 된다.",
                example = "false")
        boolean preparing,

        @Schema(description = "추천 생성이 실패했는지. 서버가 5분 주기로 자동 재시도하므로 기다리면 된다.",
                example = "false")
        boolean failed
) {

    /** 마지막 회차의 남은 유료 재추천 계산과 같은 상한. 라운드가 없으면 한 번도 안 썼다는 뜻이다. */
    private static final int MAX_PAID_RERECOMMEND = 5;

    /**
     * 추천 라운드가 아직 만들어지지 않은 상태.
     *
     * <p><b>이건 에러가 아니다.</b> 최초 추천은 착수금 결제(모집 시작) 이벤트를 받아 비동기로 돌고
     * LLM 호출까지 포함해 수 초~수십 초가 걸린다. 그 사이 클라이언트가 추천 후보 탭을 열면 라운드가
     * 없는 게 정상이다. 예전에는 이때 {@code MT_001}(추천 라운드를 찾을 수 없습니다)을 404로 내보내서,
     * 화면에 빨간 에러가 뜨고 "다시 시도"를 눌러야 후보가 보였다.
     *
     * <p>정상적인 대기 상태이므로 200으로 내리고 {@code preparing}으로 구분한다. 프론트는 로딩 안내를
     * 띄우고, 완료 알림({@code MATCHING_RECOMMENDED})을 받거나 잠시 뒤 다시 조회하면 된다.
     *
     * <p>{@code headcount}는 채워서 보낸다 — "0/4명" 같은 표기를 대기 중에도 그릴 수 있어야 한다.
     */
    public static CandidateListResponse preparing(Long positionId, int headcount) {
        return new CandidateListResponse(positionId, null, 0, null, headcount,
                false, MAX_PAID_RERECOMMEND, false, false, List.of(), true, false);
    }
}
