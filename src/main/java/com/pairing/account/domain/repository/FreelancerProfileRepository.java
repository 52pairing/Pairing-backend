package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.FreelancerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreelancerProfileRepository {

    FreelancerProfile save(FreelancerProfile freelancerProfile);

    Optional<FreelancerProfile> findByAccountId(Long accountId);

    /**
     * 여러 계정의 프로필을 한 번에. 목록 화면이 계정마다 {@link #findByAccountId} 를 부르면
     * N+1 이라 만들었다. 프로필이 없는 계정은 결과에서 빠진다.
     */
    List<FreelancerProfile> findByAccountIdIn(Collection<Long> accountIds);

    /** 매칭/협상 도메인은 계정이 아니라 이 프로필의 id 로 프리랜서를 가리킨다. */
    Optional<FreelancerProfile> findById(Long id);

    /**
     * 여러 프로필을 id 로 한 번에. 후보 목록이 항목마다 {@link #findById} 를 부르면 N+1 이라 만들었다.
     * 삭제된 프로필과 없는 id 는 결과에서 빠진다.
     */
    List<FreelancerProfile> findByIdIn(Collection<Long> ids);

    /**
     * 주어진 계정 중 활성 계정이면서 AI 매칭에 동의했고 매칭을 일시중지하지 않은 계정 id 만 돌려준다.
     *
     * <p>프로필 전체를 매핑하지 않고 id 만 읽는다. 후보 집계용이라 다른 필드가 필요 없다.
     */
    List<Long> filterActiveAiMatchingAgreed(Collection<Long> accountIds);
}
