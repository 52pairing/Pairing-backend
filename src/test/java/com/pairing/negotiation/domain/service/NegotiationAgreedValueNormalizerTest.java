package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.ConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 제안값 정규화·검증 규칙. Spring 없이 순수 로직만 본다.
 *
 * <p>실제 Gemini 호출에서 나온 값들이 회귀 기준이다: WORK_STYLE 에 없는 값 {@code HYBRID},
 * PERIOD 에 단위가 빠진 {@code "3"}.
 */
class NegotiationAgreedValueNormalizerTest {

    @Nested
    @DisplayName("WORK_STYLE / WORK_FORM (선택형)")
    class EnumTypes {

        @Test
        @DisplayName("없는 값(HYBRID)은 거부한다 — 실제 Gemini 가 만들어 낸 값")
        void rejectsHallucinatedEnum() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.WORK_STYLE, "HYBRID", null))
                    .isEmpty();
        }

        @Test
        @DisplayName("정의된 값은 그대로 통과한다")
        void acceptsDefinedEnum() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.WORK_STYLE, "REMOTE", null))
                    .contains("REMOTE");
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.WORK_FORM, "FULL_TIME", null))
                    .contains("FULL_TIME");
        }

        @Test
        @DisplayName("소문자·공백 표기는 enum 이름으로 맞춰 준다")
        void normalizesCasingAndSpaces() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.WORK_FORM, "full time", null))
                    .contains("FULL_TIME");
        }

        @Test
        @DisplayName("한글 라벨(재택)은 코드가 아니므로 거부한다")
        void rejectsKoreanLabel() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.WORK_STYLE, "재택", null))
                    .isEmpty();
        }

        @Test
        @DisplayName("허용값 목록을 AI 에게 줄 수 있다")
        void exposesAllowedValues() {
            assertThat(NegotiationAgreedValueNormalizer.allowedValues(ConditionType.WORK_STYLE))
                    .containsExactlyInAnyOrder("REMOTE", "ONSITE", "ANY");
            assertThat(NegotiationAgreedValueNormalizer.allowedValues(ConditionType.AMOUNT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("PERIOD (기간)")
    class Period {

        @Test
        @DisplayName("단위가 빠지면 기존 값의 단위로 복원한다 — 실제 Gemini 응답이 \"3\" 이었다")
        void restoresUnitFromReference() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "3", "6 MONTH"))
                    .contains("3 MONTH");
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "5", "2 WEEK"))
                    .contains("5 WEEK");
        }

        @Test
        @DisplayName("기존 값도 없으면 MONTH 로 본다")
        void defaultsToMonth() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "4", null))
                    .contains("4 MONTH");
        }

        @Test
        @DisplayName("이미 올바른 표기는 그대로 둔다")
        void keepsWellFormed() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "4 MONTH", "6 MONTH"))
                    .contains("4 MONTH");
        }

        @Test
        @DisplayName("한글 단위(개월)도 계약 표기로 바꾼다")
        void normalizesKoreanUnit() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "3개월", null))
                    .contains("3 MONTH");
        }

        @Test
        @DisplayName("숫자가 아니면 거부한다")
        void rejectsNonNumeric() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.PERIOD, "협의 후 결정", null))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("AMOUNT (금액)")
    class Amount {

        @Test
        @DisplayName("콤마·원 표기를 숫자만 남긴다")
        void stripsFormatting() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.AMOUNT, "42,000,000원", null))
                    .contains("42000000");
        }

        @Test
        @DisplayName("순수 숫자는 그대로 통과한다")
        void acceptsPlainDigits() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.AMOUNT, "41000000", null))
                    .contains("41000000");
        }

        @Test
        @DisplayName("해석이 갈리는 축약 표기(4200만)는 거부한다 — 잘못 읽으면 100배 틀린다")
        void rejectsAbbreviated() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.AMOUNT, "4200만", null))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("START_DATE (시작일)")
    class StartDate {

        @Test
        @DisplayName("ISO 날짜는 통과한다")
        void acceptsIso() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.START_DATE, "2026-09-15", null))
                    .contains("2026-09-15");
        }

        @Test
        @DisplayName("형식이 다르면 거부한다")
        void rejectsOtherFormats() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.START_DATE, "2026/09/15", null))
                    .isEmpty();
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.START_DATE, "9월 중순", null))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("SCOPE / OTHER (자유 텍스트)")
    class FreeText {

        @Test
        @DisplayName("자유 텍스트는 다듬기만 하고 통과시킨다")
        void passesThrough() {
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.SCOPE, "  프론트 개발만 ", null))
                    .contains("프론트 개발만");
            assertThat(NegotiationAgreedValueNormalizer.normalize(ConditionType.OTHER, "월 1회 대면", null))
                    .contains("월 1회 대면");
        }

        @Test
        @DisplayName("형식 힌트가 없다")
        void hasNoFormatHint() {
            assertThat(NegotiationAgreedValueNormalizer.valueFormat(ConditionType.SCOPE)).isNull();
            assertThat(NegotiationAgreedValueNormalizer.valueFormat(ConditionType.PERIOD)).isEqualTo("<숫자> MONTH");
        }
    }

    @Test
    @DisplayName("빈 값·null 은 어떤 타입이든 거부한다")
    void rejectsBlank() {
        for (ConditionType type : ConditionType.values()) {
            assertThat(NegotiationAgreedValueNormalizer.normalize(type, null, null)).isEmpty();
            assertThat(NegotiationAgreedValueNormalizer.normalize(type, "   ", null))
                    .as("type=%s", type)
                    .isEqualTo(Optional.empty());
        }
    }
}
