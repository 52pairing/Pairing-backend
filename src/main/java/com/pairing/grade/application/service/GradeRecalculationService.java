package com.pairing.grade.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.grade.application.result.GradeSnapshot;
import com.pairing.grade.application.usecase.GradeCommandUseCase;
import com.pairing.grade.domain.model.GradeTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 월간 등급 산정. (정책 P01)
 *
 * <p>지금까지 등급은 가입 시 값이 들어간 뒤 아무도 바꾸지 않았다. 기준표({@link GradeTier})는
 * 정책과 맞는데 그걸 <b>집행하는 코드가 없었다.</b> 조건을 채운 회원이 계속 기본 등급에 머물렀고,
 * 등급에 걸린 혜택(수수료 인하·프로젝트 등록 수·매칭 가중치)이 아무에게도 적용되지 않았다.
 *
 * <h2>판정 규칙 — 산정 시점의 상태로만 정한다</h2>
 * <pre>
 * 유지 기준(최근 N개월 내 완료 프로젝트) 미달  →  기본 등급 (실버 / 주니어)
 * 충족                                        →  평점·완료 건수로 산정한 등급 그대로
 * </pre>
 *
 * <p><b>이전 등급을 참고하지 않는다.</b> 등급은 달성해서 쌓아 두는 훈장이 아니라 "지금 이 사람이
 * 어떤 상태인가"를 나타내는 값이다. 그래서 <b>마스터였더라도 시니어 조건에 못 미치면 주니어</b>가
 * 된다. 한 단계씩 내리는 완충은 두지 않는다 — 등급이 실제 상태보다 높게 남는 기간이 생기고,
 * 그동안 수수료 인하 같은 혜택이 자격 없이 나간다.
 *
 * <p>평점이 떨어져도 마찬가지다. 별점 평균이 기준 아래로 내려가면 그 등급을 유지할 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradeRecalculationService implements GradeCommandUseCase {

    /** 한 번에 읽는 계정 수. 회원이 늘어도 배치가 메모리를 통째로 잡지 않게 나눠 읽는다. */
    private static final int PAGE_SIZE = 200;

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final GradeCalculator gradeCalculator;

    @Override
    public int recalculateAll() {
        int changed = recalculateRole(Role.CLIENT) + recalculateRole(Role.FREELANCER);
        log.info("[등급 산정 완료] 변경 {}건", changed);
        return changed;
    }

    /**
     * 계정 단위로 트랜잭션이 끊긴다({@code applyGrade} 가 각자 열고 닫는다).
     *
     * <p>전체를 한 트랜잭션으로 묶으면 한 계정에서 실패했을 때 그 달 산정이 통째로 사라진다.
     * 등급은 계정마다 독립이라 같이 롤백될 이유가 없다.
     */
    private int recalculateRole(Role role) {
        int changed = 0;
        Long afterId = 0L;

        while (true) {
            List<Long> accountIds = accountQueryUseCase.findActiveAccountIdsByRole(role, afterId, PAGE_SIZE);
            if (accountIds.isEmpty()) {
                return changed;
            }

            for (Long accountId : accountIds) {
                changed += recalculateOne(accountId, role) ? 1 : 0;
            }
            afterId = accountIds.get(accountIds.size() - 1);
        }
    }

    /** 한 계정 실패가 나머지를 막지 않는다. 등급 하나 때문에 그 달 산정이 멈추면 안 된다. */
    private boolean recalculateOne(Long accountId, Role role) {
        try {
            String currentCode = currentGradeCode(accountId, role);
            GradeTier target = resolveTarget(role, gradeCalculator.snapshot(accountId));

            boolean changed = accountCommandUseCase.applyGrade(accountId, role, target.code());
            if (changed) {
                log.info("[등급 변경] accountId={}, {} -> {}", accountId, currentCode, target.code());
            }
            return changed;
        } catch (Exception e) {
            log.error("[등급 산정 실패 - 건너뜀] accountId={}, role={}", accountId, role, e);
            return false;
        }
    }

    /**
     * 유지 기준을 못 채우면 기본 등급이다.
     *
     * <p>완료 건수는 누적이라 줄지 않는다. 유지 기준이 없으면 <b>한 번 올라간 등급이 영구히
     * 남는다</b> — 3년 전 실적으로 마스터를 유지하는 계정이 생긴다. 유지 기준이 그걸 막는다.
     */
    private GradeTier resolveTarget(Role role, GradeSnapshot snapshot) {
        if (!maintained(role, snapshot.lastCompletedAt())) {
            return GradeTier.base(role);
        }
        return GradeTier.resolve(role, snapshot.ratingAverage(), snapshot.completedCount());
    }

    /** 유지 기준 — 최근 N개월 안에 완료한 프로젝트가 있는지. 한 건도 없으면 등급을 잃는다. */
    private boolean maintained(Role role, LocalDateTime lastCompletedAt) {
        if (lastCompletedAt == null) {
            return false;
        }
        return lastCompletedAt.isAfter(LocalDateTime.now().minusMonths(GradeTier.maintenanceMonths(role)));
    }

    private String currentGradeCode(Long accountId, Role role) {
        if (role == Role.FREELANCER) {
            return accountQueryUseCase.findFreelancerProfileByAccountId(accountId)
                    .map(FreelancerProfile::getGrade)
                    .orElse(FreelancerGrade.JUNIOR.name());
        }
        return accountQueryUseCase.getClientProfile(accountId).getGrade();
    }
}
