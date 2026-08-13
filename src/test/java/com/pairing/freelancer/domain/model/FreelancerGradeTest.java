package com.pairing.freelancer.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 저장된 등급 문자열 파싱: 값이 이상해도 조회가 500 이 되지 않는다. */
class FreelancerGradeTest {

    @Test
    @DisplayName("아는 코드는 그대로 읽는다")
    void parsesKnownCode() {
        assertThat(FreelancerGrade.of("MASTER")).isEqualTo(FreelancerGrade.MASTER);
    }

    @Test
    @DisplayName("null·빈값·모르는 코드는 기본 등급(주니어)으로 본다")
    void fallsBackToBase() {
        assertThat(FreelancerGrade.of(null)).isEqualTo(FreelancerGrade.JUNIOR);
        assertThat(FreelancerGrade.of("")).isEqualTo(FreelancerGrade.JUNIOR);
        assertThat(FreelancerGrade.of("EXPERT")).isEqualTo(FreelancerGrade.JUNIOR);
        assertThat(FreelancerGrade.of("master")).isEqualTo(FreelancerGrade.JUNIOR);
    }
}
