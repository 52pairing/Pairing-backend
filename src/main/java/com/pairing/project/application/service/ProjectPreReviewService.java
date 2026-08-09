package com.pairing.project.application.service;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.project.application.command.PreReviewCommand;
import com.pairing.project.application.port.FreelancerCandidateCounterPort;
import com.pairing.project.application.port.FreelancerCandidateCounterPort.CandidateCriteria;
import com.pairing.project.application.result.PreReviewResult;
import com.pairing.project.application.usecase.ProjectPreReviewUseCase;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 사전 검수. (정책 P02)
 *
 * <p>AI 를 쓰지 않는다. 직무와 요구 스킬로 후보 수를 세고 모집 인원과 비교하는 단순 필터다.
 * 후보가 부족해도 등록을 막지 않는다. 화면에 조건 조정을 안내할 뿐이다.
 *
 * <p>요구 스킬은 전부 보유해야 후보로 센다(AND). 정책에 기준값이 없어 검수 규칙으로 여기서 정했다.
 * 부족할 때 "요구 스킬을 줄이면 후보가 늘어난다"고 안내하려면 AND 여야 말이 된다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectPreReviewService implements ProjectPreReviewUseCase {

    private final FreelancerCandidateCounterPort candidateCounterPort;
    private final ProjectQueryUseCase projectQueryUseCase;

    @Override
    public PreReviewResult review(PreReviewCommand command) {
        List<PreReviewResult.Item> items = new ArrayList<>();
        int index = 0;

        for (PreReviewCommand.Position position : command.positions()) {
            items.add(evaluate(index++, position.jobRole(), position.headcount(), position.skills()));
        }
        return toResult(items);
    }

    @Override
    public PreReviewResult reviewRegistered(Long projectId, Long accountId) {
        Project project = projectQueryUseCase.getByIdForOwner(projectId, accountId);

        List<PreReviewResult.Item> items = new ArrayList<>();
        int index = 0;

        for (Position position : project.getPositions()) {
            items.add(evaluate(index++, position.getJobRole(), position.getHeadcount(), position.getSkills()));
        }
        return toResult(items);
    }

    /** 포지션 1건 집계. 직무가 같아도 병합하지 않는다. 화면 카드와 1:1로 붙어야 한다. */
    private PreReviewResult.Item evaluate(int positionIndex, JobRole jobRole,
                                          int headcount, List<SkillCode> skills) {
        long counted = candidateCounterPort.count(new CandidateCriteria(jobRole, skills, true));
        int candidates = (int) Math.min(counted, Integer.MAX_VALUE);
        boolean matchable = candidates >= headcount;

        return new PreReviewResult.Item(
                positionIndex, jobRole, headcount, candidates, matchable,
                matchable ? null : buildMessage(jobRole, candidates),
                matchable ? List.of() : buildSuggestions(candidates, skills.size()));
    }

    private PreReviewResult toResult(List<PreReviewResult.Item> items) {
        return new PreReviewResult(items.stream().allMatch(PreReviewResult.Item::matchable), items);
    }

    private String buildMessage(JobRole jobRole, int candidates) {
        if (candidates == 0) {
            return "현재 조건에 맞는 %s 후보가 없습니다.".formatted(jobRole.getLabel());
        }
        return "현재 조건에 맞는 %s 후보가 모집 인원보다 부족합니다.".formatted(jobRole.getLabel());
    }

    /** 화면에 불릿으로 그대로 찍는다. 후보가 0명인지 아닌지로 안내가 갈린다. */
    private List<String> buildSuggestions(int candidates, int requiredSkillCount) {
        List<String> suggestions = new ArrayList<>();

        if (requiredSkillCount > 1) {
            suggestions.add("요구 스킬을 줄이면 더 많은 후보를 확인할 수 있습니다.");
        }
        if (candidates > 0) {
            suggestions.add("모집 인원을 %d명으로 줄이면 매칭을 시작할 수 있습니다.".formatted(candidates));
        } else {
            suggestions.add("직무를 추가하거나 다른 직무로 변경해보세요.");
        }
        return suggestions;
    }
}
