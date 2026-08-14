package com.pairing.matching.presentation.api.response;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.SkillCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 추천 후보 카드. (요구사항 R02, R03)
 *
 * <p>노출 수는 모집 인원을 넘지 않으며 클라이언트 등급에 따라 달라진다.
 * 카드마다 적합 근거(fitReason)가 함께 표시된다.
 */
@Schema(description = "추천 후보")
public record CandidateResponse(

        @Schema(description = "후보 ID. 매칭 요청 발송 시 이 값을 보낸다.", example = "100")
        Long candidateId,

        @Schema(description = "프리랜서 ID", example = "7")
        Long freelancerId,

        @Schema(description = "이름", example = "홍길동")
        String name,

        @Schema(description = "프로필 이미지 URL")
        String profileImageUrl,

        @Schema(description = "직무")
        JobRole jobRole,

        @Schema(description = "경력(년)", example = "5")
        int careerYears,

        @Schema(description = "프리랜서 등급", example = "SENIOR")
        String grade,

        @Schema(description = "평균 별점", example = "4.5")
        Double ratingAverage,

        @Schema(description = "리뷰 건수", example = "12")
        int reviewCount,

        @Schema(description = "보유 스킬")
        List<SkillCode> skills,

        // 적합도 점수는 화면에 노출하지 않는다. 근거만 태그 칩으로 짧게 나열한다.
        // 예: "요구 스킬 97% 일치", "재택 근무 선호"
        @Schema(description = "AI 추천 이유 태그", example = "[\"요구 스킬 97% 일치\", \"경력 조건 충족\"]")
        List<String> fitReasons,

        @Schema(description = "희망 급여 단위")
        PayUnit payUnit,

        @Schema(description = "희망 급여(원)", example = "6500000")
        Long payAmount,

        @Schema(description = "노출 순위", example = "1")
        int rankNo,

        @Schema(description = "이미 매칭 요청을 보낸 후보인지", example = "false")
        boolean requested,

        @Schema(description = "클라이언트가 거절한 후보인지. true면 카드가 비활성으로 표시된다.", example = "false")
        boolean rejected,

        @Schema(description = "카드 상태 코드. 버튼 활성/비활성 분기에 쓴다.", example = "AVAILABLE")
        Status status,

        @Schema(description = "카드 상태 문구. 화면에 그대로 찍는다.", example = "선택 가능")
        String statusLabel
) {

    /**
     * 후보 카드 상태. {@code requested}/{@code rejected} 두 불리언에서 파생된다.
     *
     * <p><b>선택·거절해도 카드는 목록에서 사라지지 않는다</b>(2026-08-13 확정). 숨기면 클라이언트가
     * 누구에게 요청했는지 볼 수 없고, 거절은 되돌리는 API가 없는 데다 R02 예외조건 5로 다음 회차에도
     * 안 나오므로 그 후보를 영구히 잃는다. 그래서 상태를 표시하는 쪽을 택했다.
     *
     * <p><b>거절이 요청보다 우선이다.</b> 둘 다 참일 수 있는데(요청을 보낸 뒤에도 거절할 수 있다),
     * 이때 카드로 할 수 있는 일을 정하는 건 거절 쪽이다 —
     * {@code MatchingCandidate.isSelectable()}이 {@code rejected}면 무조건 false다. 서버가 막는 기준과
     * 화면 표시가 갈리지 않도록 같은 우선순위를 쓴다.
     */
    public enum Status {

        /** 고를 수 있다. */
        AVAILABLE("선택 가능"),

        /** 매칭 요청을 보냈고 프리랜서 응답을 기다린다. */
        REQUESTED("요청 보냄"),

        /** 클라이언트가 내렸다. 다시 고를 수 없고 되돌리는 API도 없다. */
        REJECTED("거절함");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static Status of(boolean requested, boolean rejected) {
            if (rejected) {
                return REJECTED;
            }
            return requested ? REQUESTED : AVAILABLE;
        }
    }
}
