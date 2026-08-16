package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.ConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 절충안 계산 검증.
 *
 * <p>라운드 상한(15회)까지 합의 못 했을 때, 양쪽이 각자 마지노선을 넘겨 만나는 중간 지점을 낸다.
 * 방향은 가드와 같다 — 프리=하한, 클라=상한.
 */
class NegotiationCompromiseCalculatorTest {

    @Nested
    @DisplayName("compromise — 양쪽이 마지노선을 넘겨 만나는 중간 지점")
    class Compromise {

        @Test
        @DisplayName("AMOUNT: 두 마지노선의 중간값 — 프리 460·클라 340 → 400")
        void amountMidpoint() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.AMOUNT, "3400000", "4600000")).contains("4000000");
        }

        @Test
        @DisplayName("PERIOD: 일 단위 중간을 월로 환산(3·6개월 → 4개월, 반올림)")
        void periodMidpointMonths() {
            // (90일 + 180일)/2 = 135일 → 135/30 = 4.5 → 반올림 5개월
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.PERIOD, "6 MONTH", "3 MONTH")).contains("5 MONTH");
        }

        @Test
        @DisplayName("START_DATE: 프리 가용 시작일 — 클라가 그날까지 당겨 맞춘다")
        void startDateUsesAvailableFrom() {
            // 클라 상한 9/22, 프리 상한 10/21, 프리 가용 시작일 10/1 → 절충 = 10/1 (프리가 더 이르게는 불가)
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.START_DATE, "2026-09-22", "2026-10-21", "2026-10-01")).contains("2026-10-01");
        }

        @Test
        @DisplayName("START_DATE: 프리가 가용일 이후로도 못 기다리면(프리 상한 < 가용일) 절충 불가")
        void startDateInfeasibleWhenMaxBeforeAvailable() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.START_DATE, "2026-09-22", "2026-09-25", "2026-10-01")).isEmpty();
        }

        @Test
        @DisplayName("WORK_STYLE: 혼합(ANY)")
        void workStyleAny() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.WORK_STYLE, "ONSITE", "REMOTE")).contains("ANY");
        }

        @Test
        @DisplayName("WORK_FORM: 모두 가능(ANY) — WORK_STYLE 과 대칭으로 통일")
        void workFormAny() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.WORK_FORM, "FULL_TIME", "PART_TIME")).contains("ANY");
        }

        @Test
        @DisplayName("SCOPE/OTHER 자유 텍스트는 절충 불가(empty)")
        void freeTextIsUncompromisable() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.OTHER, "A 안", "B 안")).isEmpty();
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.SCOPE, "A 범위", "B 범위")).isEmpty();
        }

        @Test
        @DisplayName("한쪽 마지노선을 해석할 수 없는 RANGE 는 절충 불가(empty)")
        void unparsableRangeIsUncompromisable() {
            assertThat(NegotiationCompromiseCalculator.compromise(
                    ConditionType.AMOUNT, "삼백만", "4600000")).isEmpty();
        }
    }
}
