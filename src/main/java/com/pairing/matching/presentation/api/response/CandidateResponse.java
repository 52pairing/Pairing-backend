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

        @Schema(description = "적합도 점수(0~100)", example = "87.5")
        Double fitScore,

        // 화면에는 문장이 아니라 태그 칩으로 나열된다. "요구 스킬 97% 일치", "재택 근무 선호" 처럼 짧게 준다.
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
        boolean rejected
) {
}
