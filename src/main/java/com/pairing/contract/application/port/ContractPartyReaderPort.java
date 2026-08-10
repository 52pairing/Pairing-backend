package com.pairing.contract.application.port;

/**
 * 계약 당사자 이름 조회. 계약서에 갑·을로 찍는 값이다.
 *
 * <p>계약이 들고 있는 건 {@code client_profile.id} 와 {@code freelancer_profile.id} 라
 * account 도메인에 한 번 더 물어야 이름이 나온다. 그 변환을 어댑터가 감춘다.
 */
public interface ContractPartyReaderPort {

    /** 클라이언트는 기업명을 쓴다. 없으면 null. */
    String findClientName(Long clientProfileId);

    /** 프리랜서는 계정 이름을 쓴다. 없으면 null. */
    String findFreelancerName(Long freelancerProfileId);

    /** 프리랜서 프로필의 로그인 계정. 서명 주체를 찾을 때 쓴다. 없으면 null. */
    Long findFreelancerAccountId(Long freelancerProfileId);

    /** 클라이언트 프로필의 로그인 계정. 없으면 null. */
    Long findClientAccountId(Long clientProfileId);
}
