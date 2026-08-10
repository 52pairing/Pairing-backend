package com.pairing.contract.application.port;

import com.pairing.freelancer.domain.model.FreelancerGrade;

/**
 * 프리랜서 등급 조회. 착수금 수수료 할인 판정에 쓴다. (정책 P01·P29)
 *
 * <p>등급을 정산이 직접 읽지 않고 계약이 넘겨준다. 착수금이 발생하는 시점을 아는 쪽이 계약이고,
 * 정산 도메인이 {@code freelancer_profile} 을 참조하지 않게 하려는 것이다.
 */
public interface FreelancerGradeReaderPort {

    /** 프로필이 없거나 값이 깨졌으면 할인이 없는 {@link FreelancerGrade#JUNIOR} 로 본다. */
    FreelancerGrade findGrade(Long freelancerProfileId);
}
