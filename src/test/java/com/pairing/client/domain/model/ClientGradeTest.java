package com.pairing.client.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 저장된 등급 문자열 파싱: 값이 이상해도 조회가 500 이 되지 않는다. */
class ClientGradeTest {

    @Test
    @DisplayName("아는 코드는 그대로 읽는다")
    void parsesKnownCode() {
        assertThat(ClientGrade.of("DIAMOND")).isEqualTo(ClientGrade.DIAMOND);
    }

    @Test
    @DisplayName("null·빈값·모르는 코드는 기본 등급(실버)으로 본다")
    void fallsBackToBase() {
        assertThat(ClientGrade.of(null)).isEqualTo(ClientGrade.SILVER);
        assertThat(ClientGrade.of("")).isEqualTo(ClientGrade.SILVER);
        assertThat(ClientGrade.of("BRONZE")).isEqualTo(ClientGrade.SILVER);
        assertThat(ClientGrade.of("diamond")).isEqualTo(ClientGrade.SILVER);
    }
}
