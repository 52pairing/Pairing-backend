package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.FreelancerProfile;

import java.util.Optional;

public interface FreelancerProfileRepository {

    FreelancerProfile save(FreelancerProfile freelancerProfile);

    Optional<FreelancerProfile> findByAccountId(Long accountId);
}
