package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.freelancer.application.usecase.FreelancerCandidateCountUseCase;
import com.pairing.freelancer.domain.repository.FreelancerConditionRepository;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 조건에 맞는 프리랜서 수 집계. (프로젝트 사전 검수, 정책 P02)
 *
 * <p>두 단계로 나눈다. 1차는 이 도메인이 아는 것(직무·스킬·이력서)으로 후보 계정을 추리고,
 * 2차는 account 도메인에 그 계정들의 자격(활성·AI 매칭 동의)을 한 번에 물어본다.
 *
 * <p>한 쿼리로 조인하면 빠르지만 account 의 엔티티 구조에 묶인다. 그쪽이 컬럼을 바꾸면
 * 이 쿼리가 같이 깨지므로 포트를 거친다. 계정마다 되묻지 않고 목록으로 한 번에 물어 N+1 도 피한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FreelancerCandidateCountService implements FreelancerCandidateCountUseCase {

    private final FreelancerConditionRepository conditionRepository;
    private final AccountQueryUseCase accountQueryUseCase;

    @Override
    public long count(CandidateQuery query) {
        if (query == null || query.jobRole() == null
                || query.skills() == null || query.skills().isEmpty()) {
            return 0L;
        }

        // 중복 스킬이 들어오면 ALL 의 기준 개수가 부풀어 아무도 통과하지 못한다. 여기서 접는다.
        Set<SkillCode> skills = new LinkedHashSet<>(query.skills());
        int minMatchCount = query.skillMatch() == SkillMatch.ANY ? 1 : skills.size();

        List<Long> candidateAccountIds =
                conditionRepository.findMatchableAccountIds(query.jobRole(), skills, minMatchCount);
        if (candidateAccountIds.isEmpty()) {
            return 0L;
        }

        return accountQueryUseCase.filterActiveAiMatchingAgreed(candidateAccountIds).size();
    }
}
