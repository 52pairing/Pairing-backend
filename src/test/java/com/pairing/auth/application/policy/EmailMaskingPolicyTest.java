package com.pairing.auth.application.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 아이디 찾기 마스킹: 앞 2글자만 공개하고 도메인은 그대로 둔다. */
class EmailMaskingPolicyTest {

    @Test
    @DisplayName("로컬파트 앞 2글자만 남기고 마스킹한다")
    void masksLocalPart() {
        assertThat(EmailMaskingPolicy.mask("abcdefg@gmail.com")).isEqualTo("ab*****@gmail.com");
    }

    @Test
    @DisplayName("로컬파트가 2글자 이하면 첫 글자만 공개한다")
    void masksShortLocalPart() {
        assertThat(EmailMaskingPolicy.mask("ab@gmail.com")).isEqualTo("a*@gmail.com");
        assertThat(EmailMaskingPolicy.mask("a@gmail.com")).isEqualTo("a@gmail.com");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 그대로 반환한다")
    void returnsAsIsWhenNotEmail() {
        assertThat(EmailMaskingPolicy.mask("not-an-email")).isEqualTo("not-an-email");
        assertThat(EmailMaskingPolicy.mask(null)).isNull();
    }
}
