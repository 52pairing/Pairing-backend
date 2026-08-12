package com.pairing.grade.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.global.exception.BusinessException;
import com.pairing.grade.application.result.MyGradeResult;
import com.pairing.grade.application.usecase.GradeQueryUseCase;
import com.pairing.grade.domain.model.GradeTier;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GradeQueryService implements GradeQueryUseCase {

    /** 승급 조건이 5건이라 그 이상은 셀 이유가 없지만, 화면에 현재 건수를 그대로 보여줘서 넉넉히 잡는다. */
    private static final int COUNT_LIMIT = 200;

    private final AccountQueryUseCase accountQueryUseCase;
    private final ReviewUseCase reviewUseCase;
    private final ContractQueryUseCase contractQueryUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;

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
        int completedProjectCount = countCompletedContracts(accountId);

        return new MyGradeResult(
                current.code(),
                current.label(),
                completedProjectCount,
                ratingAverage,
                next == null ? null : next.code(),
                buildNextGradeGuide(next, ratingAverage, completedProjectCount),
                "매월 1일 자동 산정"
        );
    }

    /**
     * 대금 지급까지 끝난 계약 건수. 승급 조건의 "완료 건수"가 이 값이다.
     *
     * <p>성공보수 수수료까지 결제되어 프로젝트가 CLOSED 가 된 건만 센다. 계약의 COMPLETED 는
     * 검수 완료 시점이라 성공보수 결제 전이어서 쓰지 않는다.
     *
     * <p><b>리뷰 작성 조건과 완전히 같지는 않다.</b> 리뷰는 "본인이 성공보수를 냈는지"까지 보는데
     * 여기서는 보지 않는다. 등급은 거래를 끝냈다는 실적이고, 수수료 납부는 그 뒤의 정산 문제라
     * 지금은 나눠 뒀다. 같이 가야 한다고 정해지면 {@code ReviewService.hasPaidOwnSuccessFee} 와
     * 같은 조건을 추가하면 된다.
     *
     * <p>계약 도메인에 "프로젝트 상태로 거르는 건수 포트"가 생기면 그걸로 갈아탄다.
     */
    private int countCompletedContracts(Long accountId) {
        return (int) contractQueryUseCase.findMine(accountId, null, null, PageRequest.of(0, COUNT_LIMIT))
                .getContent().stream()
                .filter(summary -> isSettled(summary.contract()))
                .count();
    }

    /** 대금 지급까지 끝난 계약인지. 파기·거부된 계약은 완료로 세지 않는다. */
    private boolean isSettled(Contract contract) {
        if (contract.getStatus() != ContractStatus.SIGNED && contract.getStatus() != ContractStatus.COMPLETED) {
            return false;
        }
        try {
            return projectQueryUseCase.getById(contract.getProjectId()).getStatus() == ProjectStatus.CLOSED;
        } catch (BusinessException e) {
            return false;
        }
    }

    private String resolveCurrentGradeCode(Long accountId, Role role) {
        if (role == Role.FREELANCER) {
            return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                    .map(FreelancerProfile::getGrade)
                    .orElse(FreelancerGrade.JUNIOR.name());
        }
        return accountQueryUseCase.getClientProfile(accountId).getGrade();
    }

    private String buildNextGradeGuide(GradeTier next, Double ratingAverage, int completedProjectCount) {
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

        int remaining = next.minCompletedForNext() - completedProjectCount;
        String completedPart = remaining <= 0
                ? "완료 건수 조건도 충족했습니다."
                : "완료 프로젝트가 %d건 더 필요합니다(현재 %d건).".formatted(remaining, completedProjectCount);

        return ratingPart + " " + completedPart;
    }
}
