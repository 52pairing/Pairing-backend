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

        // 임베딩 재생성은 하지 않는다(예전 TODO 삭제, 2026-08-11).
        // 임베딩 대상 텍스트는 자기소개+경력사항뿐이고(`.ai/STATE.md` "확정된 설계 결정 1"),
        // 여기서 바꾸는 직군·직무·스킬·단가·연차는 그 텍스트에 안 들어간다 — 다시 만들어도 같은 벡터다.
        // 이 값들은 AI 서버가 매칭할 때 freelancer_condition 을 직접 조인해서 읽으므로
        // (Pairing-python: embedding/repository.py 하드필터, matching/repository.py Stage E 프롬프트)
        // 저장하는 즉시 반영된다. 자기소개·경력을 고치는 쪽은 ResumeService 가 ResumeUpdatedEvent 를
        // 발행해서 matching.ResumeUpdatedEventListener 가 임베딩을 다시 만든다.
        return freelancerConditionRepository.save(condition);
    }
}
