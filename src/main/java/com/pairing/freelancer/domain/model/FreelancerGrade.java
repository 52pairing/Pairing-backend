package com.pairing.freelancer.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 프리랜서 등급. 평균 별점과 완료 건수로 산정된다. (시니어 3.0·5건 / 마스터 4.0·10건) */
@Getter
@RequiredArgsConstructor
public enum FreelancerGrade {

    JUNIOR("주니어"),
    SENIOR("시니어"),
    MASTER("마스터");

    private final String label;
}
