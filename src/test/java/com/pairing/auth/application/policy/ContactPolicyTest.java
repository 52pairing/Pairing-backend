package com.pairing.auth.application.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 정규화를 빠뜨리면 같은 사람이 다른 계정으로 중복 가입된다. */
class ContactPolicyTest {

    @Test
    @DisplayName("이메일은 공백을 제거하고 소문자로 정규화한다")
    void normalizesEmail() {
        assertThat(ContactPolicy.normalizeEmail("  User@Pairing.COM ")).isEqualTo("user@pairing.com");
        assertThat(ContactPolicy.normalizeEmail(null)).isNull();
    }

    @Test
    @DisplayName("전화번호는 숫자만 남긴다")
    void normalizesPhone() {
        assertThat(ContactPolicy.normalizePhone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(ContactPolicy.normalizePhone("010 1234 5678")).isEqualTo("01012345678");
        assertThat(ContactPolicy.normalizePhone(null)).isNull();
    }

    @Test
    @DisplayName("같은 값은 같은 해시가 나오고, 다른 값은 달라진다")
    void hashesConsistently() {
        String hash = ContactPolicy.sha256("user@pairing.com");

        assertThat(hash).hasSize(64)
                .isEqualTo(ContactPolicy.sha256("user@pairing.com"))
                .isNotEqualTo(ContactPolicy.sha256("other@pairing.com"));
    }
}
