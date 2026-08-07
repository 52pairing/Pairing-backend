package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.FreelancerCondition;

import java.util.Optional;

public interface FreelancerConditionRepository {

    FreelancerCondition save(FreelancerCondition condition);

    Optional<FreelancerCondition> findByAccountId(Long accountId);
}
