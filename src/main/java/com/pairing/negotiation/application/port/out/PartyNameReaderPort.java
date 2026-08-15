package com.pairing.negotiation.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 협상 표시용 당사자 이름 읽기 포트(협상 소유). account/client_profile 은 팀원 도메인이므로
 * project 와 동일하게 협상이 읽기 전용으로 직접 조회한다(쓰기 없음).
 */
public interface PartyNameReaderPort {

    /** 프리랜서 이름 = freelancer_profile.account_id → account.name. */
    Optional<String> findFreelancerName(Long freelancerProfileId);

    /** 클라이언트 표시명 = client_profile.company_name (회사명). */
    Optional<String> findClientCompanyName(Long clientProfileId);

    /**
     * 여러 프리랜서 이름을 한 번에. 목록은 카드마다 이름을 읽으면 N+1 이 되므로 IN 절로 모은다.
     * 없는 ID 는 맵에서 빠진다.
     */
    Map<Long, String> findFreelancerNames(Collection<Long> freelancerProfileIds);

    /** 여러 클라이언트 회사명을 한 번에. */
    Map<Long, String> findClientCompanyNames(Collection<Long> clientProfileIds);
}
