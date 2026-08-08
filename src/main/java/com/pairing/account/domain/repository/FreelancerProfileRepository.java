package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.FreelancerProfile;

import java.util.Optional;

public interface FreelancerProfileRepository {

    FreelancerProfile save(FreelancerProfile freelancerProfile);

    Optional<FreelancerProfile> findByAccountId(Long accountId);

    /** 매칭/협상 도메인은 계정이 아니라 이 프로필의 id 로 프리랜서를 가리킨다. */
    Optional<FreelancerProfile> findById(Long id);
}
