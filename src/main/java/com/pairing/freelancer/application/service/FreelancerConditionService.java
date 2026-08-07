package com.pairing.freelancer.application.service;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.domain.model.ConditionSkill;
import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.freelancer.domain.repository.FreelancerConditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class FreelancerConditionService implements FreelancerConditionUseCase {

    private final FreelancerConditionRepository freelancerConditionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<FreelancerCondition> findMyCondition(Long accountId) {
        return freelancerConditionRepository.findByAccountId(accountId);
    }

    @Override
    public FreelancerCondition upsert(UpsertConditionCommand command) {
        List<ConditionSkill> skills = command.skills().stream()
                .map(skill -> ConditionSkill.of(skill.skillCode(), skill.skillLevel()))
                .toList();

        FreelancerCondition condition = freelancerConditionRepository.findByAccountId(command.accountId())
                .map(existing -> {
                    existing.replaceWith(command.jobCategory(), command.jobRole(), command.affiliation(),
                            command.workStyle(), command.workForm(), command.payUnit(), command.payAmount(),
                            command.minAcceptAmount(), command.availableFrom(), command.startNegotiable(),
                            command.periodValue(), command.periodUnit(), command.hasFreelanceExperience(),
                            command.careerYears(), skills);
                    return existing;
                })
                .orElseGet(() -> FreelancerCondition.create(command.accountId(), command.jobCategory(),
                        command.jobRole(), command.affiliation(), command.workStyle(), command.workForm(),
                        command.payUnit(), command.payAmount(), command.minAcceptAmount(),
                        command.availableFrom(), command.startNegotiable(), command.periodValue(),
                        command.periodUnit(), command.hasFreelanceExperience(), command.careerYears(), skills));

        // TODO: ai-server 연동 준비되면 저장 후 이력서 임베딩 재생성 요청
        return freelancerConditionRepository.save(condition);
    }
}
