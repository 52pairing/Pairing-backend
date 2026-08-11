package com.pairing.freelancer.application.service;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.application.event.ConditionUpdatedEvent;
import com.pairing.freelancer.application.usecase.FreelancerConditionUseCase;
import com.pairing.freelancer.domain.model.ConditionSkill;
import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.freelancer.domain.repository.FreelancerConditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class FreelancerConditionService implements FreelancerConditionUseCase {

    private final FreelancerConditionRepository freelancerConditionRepository;
    private final ApplicationEventPublisher eventPublisher;

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

        FreelancerCondition saved = freelancerConditionRepository.save(condition);

        // 직무·스킬·경력·근무방식·기간이 프리랜서 임베딩 텍스트에 들어가므로(2026-08-11부터),
        // 조건을 고치면 저장된 벡터가 낡는다. 이력서 저장(ResumeService)과 같은 방식으로 신호만
        // 보내고, 실제 재생성은 매칭이 커밋 후 비동기로 처리한다 — 임베딩 생성은 외부 AI 호출이라
        // 여기서 기다리면 조건 저장 API 응답이 그만큼 늦어진다.
        eventPublisher.publishEvent(new ConditionUpdatedEvent(command.accountId()));
        return saved;
    }
}
