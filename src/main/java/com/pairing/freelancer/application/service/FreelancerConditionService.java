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
                    existing.replaceWith(command.jobCategory(), command.jobRole(),
                            command.workStyle(), command.workForm(), command.payUnit(), command.payAmount(),
                            command.minAcceptAmount(), command.availableFrom(), command.startNegotiable(),
                            command.periodValue(), command.periodUnit(), command.hasFreelanceExperience(),
                            command.careerYears(), skills);
                    return existing;
                })
                .orElseGet(() -> FreelancerCondition.create(command.accountId(), command.jobCategory(),
                        command.jobRole(), command.workStyle(), command.workForm(),
                        command.payUnit(), command.payAmount(), command.minAcceptAmount(),
                        command.availableFrom(), command.startNegotiable(), command.periodValue(),
                        command.periodUnit(), command.hasFreelanceExperience(), command.careerYears(), skills));

        // 임베딩 재생성 신호를 보내지 않는다(예전 TODO 삭제, 2026-08-11 확정).
        // 프리랜서 임베딩 텍스트에는 문장(자기소개·경력사항)만 들어가고, 여기서 바꾸는
        // 직군·직무·스킬·연차·단가·근무조건은 안 들어간다 — 다시 만들어도 같은 벡터다.
        // 이 값들은 AI 서버가 매칭할 때 freelancer_condition 을 직접 읽어 DB 조건점수로
        // 반영하므로 저장하는 즉시 다음 추천부터 적용된다.
        // 문장을 고치는 쪽(이력서)은 ResumeService 가 ResumeUpdatedEvent 를 발행한다.
        // 근거: `.ai/STATE.md` "2026-08-11 갱신 — 매칭 파이프라인 재설계(팀 확정)"
        return freelancerConditionRepository.save(condition);
    }
}
