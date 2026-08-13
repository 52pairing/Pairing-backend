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

    /** 역할별 기본 등급(실버 / 주니어). 가입 직후이거나 실적이 없으면 이 등급이다. */
    public static GradeTier base(Role role) {
        return forRole(role).get(0);
    }

    /**
     * 등급 유지 기준 기간(개월). 이 기간 내에 완료한 프로젝트가 없으면 등급을 잃는다. (정책 P01)
     *
     * <p>프리랜서 6개월, 클라이언트 12개월이다. 프리랜서는 프로젝트가 곧 일감이라 공백이 실력
     * 신선도로 읽히고, 클라이언트는 발주 주기가 길어 6개월로 재면 정상 이용자도 걸린다.
     */
    public static int maintenanceMonths(Role role) {
        return role == Role.CLIENT ? 12 : 6;
    }

    /** 코드로 등급을 찾는다. 저장된 값이 목록에 없으면(옛 코드 등) 기본 등급으로 본다. */
    public static GradeTier ofCode(Role role, String code) {
        return forRole(role).stream()
                .filter(tier -> tier.code().equals(code))
                .findFirst()
                .orElseGet(() -> base(role));
    }

    /**
     * 평점과 완료 건수로 산정한 등급. <b>지금 상태만 본다 — 이전 등급은 보지 않는다.</b>
     *
     * <p>아래에서 위로 한 칸씩 올라가다 조건이 깨지면 거기서 멈춘다. 그래서 <b>마스터였더라도
     * 시니어 조건에 못 미치면 주니어</b>가 된다. 등급은 "달성한 훈장"이 아니라 "지금 이 사람이
     * 어떤 상태인가"를 나타내는 값이다.
     *
     * <p>한 칸씩 올라가며 보는 이유는, 상위 등급 조건만 따로 보면 "골드 조건은 못 넘겼는데
     * 다이아 조건은 넘긴" 판정이 나올 수 있어서다. 기준이 항상 단조롭게 오른다는 보장이
     * 정책에 없다.
     *
     * <p>{@code ratingAverage} 가 null 이면(리뷰 없음) 기본 등급이다. 평점 조건을 통과할 수 없다.
     */
    public static GradeTier resolve(Role role, Double ratingAverage, int completedCount) {
        List<GradeTier> tiers = forRole(role);
        GradeTier reached = tiers.get(0);

        for (int i = 0; i < tiers.size() - 1; i++) {
            GradeTier tier = tiers.get(i);
            boolean ratingMet = ratingAverage != null && ratingAverage >= tier.minRatingForNext();
            boolean countMet = completedCount >= tier.minCompletedForNext();
            if (!ratingMet || !countMet) {
                break;
            }
            reached = tiers.get(i + 1);
        }
        return reached;
    }

}
