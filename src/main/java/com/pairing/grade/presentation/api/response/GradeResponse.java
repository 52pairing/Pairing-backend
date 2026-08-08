package com.pairing.grade.presentation.api.response;

import com.pairing.account.domain.model.Role;
import com.pairing.grade.domain.model.GradeTier;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 등급 안내 한 칸. (등급 및 혜택 화면)
 *
 * <p>등급은 프로젝트 완료 실적과 평점으로 자동 산정된다. 사용자가 바꿀 수 없다.
 * 수수료율이 등급별로 다르므로 화면에 함께 노출한다.
 */
@Schema(description = "등급 안내")
public record GradeResponse(

        @Schema(description = "대상 역할") Role role,
        @Schema(description = "등급 코드", example = "GOLD") String grade,
        @Schema(description = "등급 이름", example = "골드") String label,
        @Schema(description = "정렬 순서. 낮을수록 하위 등급", example = "2") int level,

        @Schema(description = "승급 조건", example = "별점 평균 3점 이상 + 완료 건수 10건 이상")
        String promotionCondition,

        @Schema(description = "등급 유지 기준", example = "12개월 내 프로젝트 경험 · 매월 체크")
        String maintenanceCondition,

        @Schema(description = "주요 혜택") List<Benefit> benefits,
        @Schema(description = "수수료율") FeeRate feeRate,

        // 프리랜서 화면은 숫자 표 대신 "기본 수수료" / "수수료 각 1% 인하 (총 2% 인하)" 문구로 보여준다.
        @Schema(description = "수수료 표시 문구", example = "기본 수수료") String feeNote
) {

    public static GradeResponse from(GradeTier tier) {
        return new GradeResponse(
                tier.role(),
                tier.code(),
                tier.label(),
                tier.level(),
                tier.promotionCondition(),
                tier.maintenanceCondition(),
                tier.benefits().stream().map(b -> new Benefit(b.label(), b.value())).toList(),
                new FeeRate(tier.feeRate().depositUnder(), tier.feeRate().depositOver(),
                        tier.feeRate().successFeeUnder(), tier.feeRate().successFeeOver()),
                tier.feeNote()
        );
    }

    /** 화면에 "매칭 프리랜서 수 — 1명" 처럼 라벨과 값으로 나열된다. */
    @Schema(description = "혜택 항목")
    public record Benefit(
            @Schema(description = "항목", example = "프로젝트 등록") String label,
            @Schema(description = "값", example = "최대 2개") String value
    ) {
    }

    /** 계약 금액 1억 원을 기준으로 요율이 갈린다. */
    @Schema(description = "등급별 수수료율(%)")
    public record FeeRate(
            @Schema(description = "착수금 · 1억 미만", example = "3.00") BigDecimal depositUnder,
            @Schema(description = "착수금 · 1억 이상", example = "2.00") BigDecimal depositOver,
            @Schema(description = "성공보수 · 1억 미만", example = "7.00") BigDecimal successFeeUnder,
            @Schema(description = "성공보수 · 1억 이상", example = "6.00") BigDecimal successFeeOver
    ) {
    }
}
