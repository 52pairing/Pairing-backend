package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.ConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마지노선 가드 검증.
 *
 * <p>방향이 역할마다 반대라는 게 핵심이다 — 프리랜서 마지노선은 하한, 클라이언트 마지노선은 상한이다.
 * 실제로 프리 하한 480만인데 대리인이 330만을 수락한 사례가 있었고, 그걸 막는 게 이 클래스다.
 */
class NegotiationFloorGuardTest {

    @Nested
    @DisplayName("AMOUNT — 프리 하한 이상, 클라 상한 이하여야 한다")
    class Amount {

        @Test
        @DisplayName("두 선 사이 값은 통과")
        void withinBothFloors() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "3500000", "4000000", "3000000")).isTrue();
        }

        @Test
        @DisplayName("프리 하한 미만이면 차단 — 실제로 났던 버그(하한 480만인데 330만 수락)")
        void belowFreelancerFloorIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "3300000", null, "4800000")).isFalse();
        }

        @Test
        @DisplayName("클라 상한 초과면 차단")
        void aboveClientFloorIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "5000000", "4000000", null)).isFalse();
        }

        @Test
        @DisplayName("경계값은 포함(이상/이하)")
        void boundariesAreInclusive() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "4000000", "4000000", "4000000")).isTrue();
        }

        @Test
        @DisplayName("마지노선이 없으면 그 방향 제약도 없다")
        void nullFloorMeansNoBound() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "9999999", null, null)).isTrue();
        }

        @Test
        @DisplayName("해석할 수 없는 합의값은 차단 — 검증 못 하는 값을 락하느니 사람에게 넘긴다")
        void unparsableAgreedValueIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.AMOUNT, "350만원", "4000000", "3000000")).isFalse();
        }
    }

    @Nested
    @DisplayName("PERIOD — 단위가 섞여도 순서 비교가 된다")
    class Period {

        @Test
        @DisplayName("프리 최소 4개월 ~ 클라 최대 6개월 사이의 5개월은 통과")
        void withinBothFloors() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.PERIOD, "5 MONTH", "6 MONTH", "4 MONTH")).isTrue();
        }

        @Test
        @DisplayName("클라 상한을 넘긴 기간은 차단")
        void aboveClientFloorIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.PERIOD, "8 MONTH", "6 MONTH", "4 MONTH")).isFalse();
        }

        @Test
        @DisplayName("주 단위와 월 단위를 섞어도 비교된다")
        void mixedUnitsAreComparable() {
            // 2 WEEK(14일) < 1 MONTH(30일) 이므로 하한 1개월을 못 넘긴다.
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.PERIOD, "2 WEEK", null, "1 MONTH")).isFalse();
        }

        @Test
        @DisplayName("단위 없는 값은 차단 — /start 에서 이미 정규화됐어야 한다")
        void unitlessValueIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.PERIOD, "5", "6 MONTH", "4 MONTH")).isFalse();
        }
    }

    @Nested
    @DisplayName("WORK_STYLE — 크기가 아니라 양측이 허용했는지를 본다")
    class WorkStyle {

        @Test
        @DisplayName("양측이 같은 값을 허용하면 통과")
        void bothAllowSameValue() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.WORK_STYLE, "REMOTE", "REMOTE", "REMOTE")).isTrue();
        }

        @Test
        @DisplayName("ANY 는 무엇이든 허용")
        void anyAllowsEverything() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.WORK_STYLE, "ONSITE", "ANY", "ANY")).isTrue();
        }

        @Test
        @DisplayName("한쪽만 허용하는 값은 차단 — 실제로 났던 버그(재택만 허용인데 상주 수락)")
        void valueAllowedByOnlyOneSideIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.WORK_STYLE, "ONSITE", "ONSITE", "REMOTE")).isFalse();
        }

        @Test
        @DisplayName("양측이 겹치지 않는데 ANY 로 합의한 것도 차단")
        void inventedCompromiseIsRejected() {
            assertThat(NegotiationFloorGuard.respectsFloors(
                    ConditionType.WORK_STYLE, "ANY", "ONSITE", "REMOTE")).isFalse();
        }
    }

    @Test
    @DisplayName("자유 텍스트(SCOPE/OTHER)는 비교 기준이 없어 통과시킨다")
    void freeTextPasses() {
        assertThat(NegotiationFloorGuard.respectsFloors(
                ConditionType.OTHER, "협의 내용", "무엇이든", "무엇이든")).isTrue();
    }

    @Test
    @DisplayName("합의값이 비어 있으면 차단")
    void blankAgreedValueIsRejected() {
        assertThat(NegotiationFloorGuard.respectsFloors(ConditionType.AMOUNT, "  ", null, null)).isFalse();
    }
}
