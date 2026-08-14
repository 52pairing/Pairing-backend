package com.pairing.freelancer.application.result;

import com.pairing.account.domain.model.Address;
import com.pairing.freelancer.domain.model.FreelancerGrade;

import java.time.LocalDate;

/** 프리랜서 마이페이지. 결제수단은 계정 공통 화면(06번 도메인)이 따로 담당해 여기 없다. */
public record FreelancerMyPageResult(
        Long accountId,
        String name,
        String email,
        String phone,
        LocalDate birthDate,
        /** 한 줄로 합친 주소. 화면에 한 줄만 찍는 곳이 쓴다. */
        String address,
        /** 나눠 담긴 주소. 수정 폼이 쓴다. 나눠 담기 전 가입한 계정은 null. */
        Address addressParts,
        String profileImageUrl,
        boolean aiMatchingAgreed,
        FreelancerGrade grade,
        Double ratingAverage,
        int reviewCount,
        boolean resumeCompleted,
        boolean withdrawable
) {
}
