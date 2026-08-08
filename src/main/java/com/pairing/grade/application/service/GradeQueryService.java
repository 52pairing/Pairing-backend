package com.pairing.grade.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.grade.application.result.MyGradeResult;
import com.pairing.grade.application.usecase.GradeQueryUseCase;
import com.pairing.grade.domain.model.GradeTier;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GradeQueryService implements GradeQueryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ReviewUseCase reviewUseCase;

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

        Double ratingAverage = reviewUseCase.getSummary(accountId).averageScore();
        // TODO: contract 도메인 구현되면 실제 완료 건수로 교체
        int completedProjectCount = 0;

        return new MyGradeResult(
                current.code(),
                current.label(),
                completedProjectCount,
                ratingAverage,
                next == null ? null : next.code(),
                buildNextGradeGuide(next, ratingAverage),
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

    private String buildNextGradeGuide(GradeTier next, Double ratingAverage) {
        if (next == null) {
            return null;
        }
        String ratingPart;
        if (ratingAverage == null) {
            ratingPart = "아직 리뷰가 없어 별점 조건을 확인할 수 없습니다.";
        } else if (ratingAverage >= next.minRatingForNext()) {
            ratingPart = "별점 조건은 충족했습니다.";
        } else {
            ratingPart = "별점 평균 %.1f점 이상이 필요합니다(현재 %.1f점)."
                    .formatted(next.minRatingForNext(), ratingAverage);
        }
        return ratingPart + " 완료 건수 조건은 계약 도메인 구현 후 정확히 표시됩니다.";
    }
}
