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

    /**
     * 저장된 등급 코드를 등급으로 바꾼다. 비어 있거나 목록에 없는 코드(옛 코드 등)면 기본 등급이다.
     *
     * <p>DB 문자열을 {@code valueOf} 로 바로 파싱하면 값이 하나 어긋난 계정에서 조회 API 가
     * 통째로 500 이 된다. 등급은 화면의 한 줄이라 마이페이지를 못 열게 만들 이유가 없다.
     */
    public static FreelancerGrade of(String code) {
        for (FreelancerGrade grade : values()) {
            if (grade.name().equals(code)) {
                return grade;
            }
        }
        return JUNIOR;
    }
}
