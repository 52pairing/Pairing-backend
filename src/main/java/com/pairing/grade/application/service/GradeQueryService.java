package com.pairing.grade.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.grade.application.result.GradeSnapshot;
import com.pairing.grade.application.result.MyGradeResult;
import com.pairing.grade.application.usecase.GradeQueryUseCase;
import com.pairing.grade.domain.model.GradeTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GradeQueryService implements GradeQueryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final GradeCalculator gradeCalculator;

    @Override
    public List<GradeTier> findByRole(Role role) {
        return GradeTier.forRole(role);
    }

    @Override
    public MyGradeResult getMyGrade(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);
        Role role = account.getRole();

        List<GradeTier> tiers = GradeTier.forRole(role);
        String currentCode = resolveCurrentGradeCode(accountId, role);
        GradeTier current = tiers.stream()
                .filter(tier -> tier.code().equals(currentCode))
                .findFirst()
                .orElse(tiers.get(0));
        GradeTier next = tiers.stream()
                .filter(tier -> tier.level() == current.level() + 1)
                .findFirst()
                .orElse(null);

        // 월간 산정 배치와 같은 계산기를 쓴다. 각자 계산하면 "조건을 충족했습니다"라고 안내한
        // 다음 달에 승급이 안 되는 일이 생긴다.
        GradeSnapshot snapshot = gradeCalculator.snapshot(accountId);

        return new MyGradeResult(
                current.code(),
                current.label(),
                snapshot.completedCount(),
                snapshot.ratingAverage(),
                next == null ? null : next.code(),
                buildNextGradeGuide(current, next, snapshot.ratingAverage(), snapshot.completedCount()),
                "매월 1일 자동 산정"
        );
    }

    private String resolveCurrentGradeCode(Long accountId, Role role) {
        if (role == Role.FREELANCER) {
            return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                    .map(FreelancerProfile::getGrade)
                    .orElse(FreelancerGrade.JUNIOR.name());
        }
        return accountQueryUseCase.getClientProfile(accountId).getGrade();
    }

    /**
     * 다음 등급까지 남은 조건 문구. 최고 등급이면 null.
     *
     * <p>기준값은 <b>현재 등급 칸</b>에서 읽는다. {@code minRatingForNext}/{@code minCompletedForNext} 는
     * "이 등급에서 다음 등급으로 올라가는 데 필요한 값"이라 다음 등급 칸이 아니라 현재 등급 칸에 들어
     * 있다({@link GradeTier#resolve} 도 같은 방식으로 읽는다). 최고 등급 칸은 둘 다 null 이므로
     * 다음 등급 칸에서 읽으면 최고 등급 바로 아래(골드·시니어) 사용자에게서 언박싱 NPE 가 났다.
     *
     * <p>{@code next} 가 있어도 기준값이 비어 있으면(등급표에 값이 빠진 경우) 잘못된 숫자를 보여주는
     * 대신 문구를 비운다.
     */
    private String buildNextGradeGuide(GradeTier current, GradeTier next,
                                       Double ratingAverage, int completedProjectCount) {
        if (next == null || current.minRatingForNext() == null || current.minCompletedForNext() == null) {
            return null;
        }
        String ratingPart;
        if (ratingAverage == null) {
            ratingPart = "아직 리뷰가 없어 별점 조건을 확인할 수 없습니다.";
        } else if (ratingAverage >= current.minRatingForNext()) {
            ratingPart = "별점 조건은 충족했습니다.";
        } else {
            ratingPart = "별점 평균 %.1f점 이상이 필요합니다(현재 %.1f점)."
                    .formatted(current.minRatingForNext(), ratingAverage);
        }

        int remaining = current.minCompletedForNext() - completedProjectCount;
        String completedPart = remaining <= 0
                ? "완료 건수 조건도 충족했습니다."
                : "완료 프로젝트가 %d건 더 필요합니다(현재 %d건).".formatted(remaining, completedProjectCount);

        return ratingPart + " " + completedPart;
    }
}
