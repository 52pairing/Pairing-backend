package com.pairing.freelancer.application.result;

import com.pairing.freelancer.domain.model.FreelancerGrade;

import java.time.LocalDate;

/** 프리랜서 마이페이지. 결제수단은 계정 공통 화면(06번 도메인)이 따로 담당해 여기 없다. */
public record FreelancerMyPageResult(
        Long accountId,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        String address,
        String profileImageUrl,
        boolean aiMatchingAgreed,
        FreelancerGrade grade,
        Double ratingAverage,
        int reviewCount,
        boolean resumeCompleted,
        boolean withdrawable
) {
}
