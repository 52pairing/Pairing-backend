package com.pairing.freelancer.application.usecase;

import com.pairing.freelancer.application.command.UpsertConditionCommand;
import com.pairing.freelancer.domain.model.FreelancerCondition;

import java.util.Optional;

public interface FreelancerConditionUseCase {

    /** 등록하지 않았으면 empty. */
    Optional<FreelancerCondition> findMyCondition(Long accountId);

    /** 없으면 생성하고 있으면 전체 교체한다. */
    FreelancerCondition upsert(UpsertConditionCommand command);
}
