package com.pairing.grade.domain.model;

import com.pairing.account.domain.model.Role;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.global.exception.BusinessException;
import com.pairing.grade.exception.GradeErrorCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 등급 기준표 한 칸. (요구사항 07. Grade)
 *
 * <p>등급은 완료 실적과 평점으로 자동 산정되고 변경 API가 없는 고정 정책이라, DB가 아니라
 * 이 클래스의 정적 목록이 유일한 소스다. {@code minRatingForNext}/{@code minCompletedForNext} 는
 * 화면의 승급 조건 문구뿐 아니라 {@code GET /grades/me} 의 다음 등급 계산에도 그대로 재사용된다.
 * 최고 등급이면 둘 다 null이다.
 */
public record GradeTier(
        Role role,
        String code,
        String label,
        int level,
        String promotionCondition,
        String maintenanceCondition,
        List<Benefit> benefits,
        FeeRate feeRate,
        String feeNote,
        Double minRatingForNext,
        Integer minCompletedForNext
) {

    public record Benefit(String label, String value) {
    }

    public record FeeRate(BigDecimal depositUnder, BigDecimal depositOver,
                          BigDecimal successFeeUnder, BigDecimal successFeeOver) {
    }

    private static final String CLIENT_MAINTENANCE = "12개월 내 프로젝트 경험 · 매월 체크";
    private static final String FREELANCER_MAINTENANCE = "6개월 내 프로젝트 경험 유지";

    private static final FeeRate BASE_RATE = new FeeRate(
            new BigDecimal("3.00"), new BigDecimal("2.00"),
            new BigDecimal("7.00"), new BigDecimal("6.00"));

    private static final FeeRate TOP_RATE = new FeeRate(
            new BigDecimal("2.00"), new BigDecimal("1.00"),
            new BigDecimal("6.00"), new BigDecimal("5.00"));

    private static final List<GradeTier> CLIENT_TIERS = List.of(
            new GradeTier(Role.CLIENT, ClientGrade.SILVER.name(), ClientGrade.SILVER.getLabel(), 1,
                    "기본 등급", CLIENT_MAINTENANCE,
                    List.of(new Benefit("매칭 프리랜서 수", "1명"),
                            new Benefit("프로젝트 등록", "최대 1개"),
                            new Benefit("우수 프리랜서 매칭", "매칭 확률 증가")),
                    BASE_RATE, "기본 수수료", 3.0, 10),
            new GradeTier(Role.CLIENT, ClientGrade.GOLD.name(), ClientGrade.GOLD.getLabel(), 2,
                    "별점 평균 3점 이상 + 완료 건수 10건 이상", CLIENT_MAINTENANCE,
                    List.of(new Benefit("매칭 프리랜서 수", "1명"),
                            new Benefit("프로젝트 등록", "최대 2개"),
                            new Benefit("높은 등급 프리랜서 매칭", "매칭 확률 증가")),
                    BASE_RATE, "기본 수수료", 4.0, 20),
            new GradeTier(Role.CLIENT, ClientGrade.DIAMOND.name(), ClientGrade.DIAMOND.getLabel(), 3,
                    "별점 평균 4점 이상 + 완료 건수 20건 이상", CLIENT_MAINTENANCE,
                    List.of(new Benefit("매칭 프리랜서 수", "1명"),
                            new Benefit("프로젝트 등록", "최대 2개"),
                            new Benefit("높은 등급 프리랜서 매칭", "매칭 확률 증가"),
                            new Benefit("착수금 수수료", "1% 인하"),
                            new Benefit("성공보수 수수료", "1% 인하 (총 2% 인하)")),
                    TOP_RATE, "수수료 각 1% 인하 (총 2% 인하)", null, null)
    );

    private static final List<Benefit> FREELANCER_BASE_BENEFITS = List.of(
            new Benefit("표준계약서 작성", "제공"),
            new Benefit("검증된 프로젝트 매칭", "제공"),
            new Benefit("AI 1:1 맞춤 매칭", "제공"));

    private static final List<Benefit> FREELANCER_SENIOR_BENEFITS = List.of(
            new Benefit("표준계약서 작성", "제공"),
            new Benefit("검증된 프로젝트 매칭", "제공"),
            new Benefit("AI 1:1 맞춤 매칭", "제공"),
            new Benefit("우수 클라이언트 매칭", "매칭 확률 증가"));

    private static final List<GradeTier> FREELANCER_TIERS = List.of(
            new GradeTier(Role.FREELANCER, FreelancerGrade.JUNIOR.name(), FreelancerGrade.JUNIOR.getLabel(), 1,
                    "디폴트 (가입 즉시)", FREELANCER_MAINTENANCE,
                    FREELANCER_BASE_BENEFITS, BASE_RATE, "기본 수수료", 3.0, 5),
            new GradeTier(Role.FREELANCER, FreelancerGrade.SENIOR.name(), FreelancerGrade.SENIOR.getLabel(), 2,
                    "별점 3점 이상 + 완료 5건 이상", FREELANCER_MAINTENANCE,
                    FREELANCER_SENIOR_BENEFITS, BASE_RATE, "기본 수수료", 4.0, 10),
            new GradeTier(Role.FREELANCER, FreelancerGrade.MASTER.name(), FreelancerGrade.MASTER.getLabel(), 3,
                    "별점 4점 이상 + 완료 10건 이상", FREELANCER_MAINTENANCE,
                    FREELANCER_SENIOR_BENEFITS, TOP_RATE, "수수료 각 1% 인하 (총 2% 인하)", null, null)
    );

    /** 역할별 등급 기준표. CLIENT/FREELANCER 외 역할이면 {@code GR_001}. */
    public static List<GradeTier> forRole(Role role) {
        if (role == Role.CLIENT) {
            return CLIENT_TIERS;
        }
        if (role == Role.FREELANCER) {
            return FREELANCER_TIERS;
        }
        throw new BusinessException(GradeErrorCode.INVALID_ROLE);
    }
}
