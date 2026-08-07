package com.pairing.negotiation.application.port.out;

import java.util.Optional;

/**
 * 협상 당사자 신원 매핑 포트(협상 소유). 로그인 계정(account.id)을 협상이 아는 "명함 ID"로 번역한다.
 *
 * <ul>
 *   <li>협상은 프리 당사자를 {@code freelancer_profile.id} 로, 클라 당사자를 project 의
 *       {@code client_profile.id} 로만 안다.</li>
 *   <li>화면 인증은 {@code account.id} 로 들어오므로, role 판정 전에 계정→프로필 번역이 필요하다.</li>
 * </ul>
 *
 * <p>어댑터는 account 도메인의 프로필 조회 포트에 위임한다.
 */
public interface PartyProfilePort {

    /** 이 계정의 클라이언트 프로필 ID. 클라이언트로 가입한 적 없으면 empty. */
    Optional<Long> findClientProfileIdByAccountId(Long accountId);

    /** 이 계정의 프리랜서 프로필 ID. 프리랜서로 가입한 적 없으면 empty. */
    Optional<Long> findFreelancerProfileIdByAccountId(Long accountId);
}
