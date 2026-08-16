package com.pairing.negotiation.domain.model;

import com.pairing.negotiation.domain.service.NegotiationFloorGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConditionType#getFloorComparison()} 이 {@link NegotiationFloorGuard} 의 실제 동작과
 * 일치하는지 검증한다.
 *
 * <p>이 값은 화면 안내 문구를 고르는 데 쓰인다. 가드는 선택형을 허용값으로 판정하는데 화면이
 * "재택 <b>이상</b>이어야 합니다"라고 쓰고 있었다 — 규칙이 두 곳에 흩어져 어긋난 것이다.
 * 서버가 분류를 내려보내기로 했으니, <b>그 분류가 가드와 어긋나면 같은 문제가 되풀이된다.</b>
 *
 * <p>선언값만 비교하면 "둘 다 틀린" 경우를 못 잡으므로, 가드를 실제로 호출해
 * <b>분류에 맞는 동작이 나오는지</b>까지 본다.
 */
class ConditionTypeFloorComparisonTest {

    @ParameterizedTest
    @EnumSource(ConditionType.class)
    @DisplayName("모든 쟁점 종류에 비교 방식이 정해져 있다")
    void everyTypeIsClassified(ConditionType type) {
        // 새 쟁점을 추가하면 여기서 걸려 분류를 정하게 된다.
        assertThat(type.getFloorComparison()).isNotNull();
    }

    @Test
    @DisplayName("RANGE 는 구간 안이면 통과하고 밖이면 막힌다 — 크기 비교가 성립한다")
    void rangeTypesCompareByMagnitude() {
        // 금액: 프리 하한 400만 ≤ x ≤ 클라 상한 500만
        assertThat(ConditionType.AMOUNT.getFloorComparison()).isEqualTo(FloorComparison.RANGE);
        assertThat(respects(ConditionType.AMOUNT, "4500000", "5000000", "4000000")).isTrue();
        assertThat(respects(ConditionType.AMOUNT, "5500000", "5000000", "4000000")).isFalse();

        // 기간: 4개월 ≤ x ≤ 6개월
        assertThat(ConditionType.PERIOD.getFloorComparison()).isEqualTo(FloorComparison.RANGE);
        assertThat(respects(ConditionType.PERIOD, "5 MONTH", "6 MONTH", "4 MONTH")).isTrue();
        assertThat(respects(ConditionType.PERIOD, "7 MONTH", "6 MONTH", "4 MONTH")).isFalse();

        // 시작일: 양측 모두 상한(늦어도 이 날까지). x ≤ 클라 상한 and x ≤ 프리 상한.
        assertThat(ConditionType.START_DATE.getFloorComparison()).isEqualTo(FloorComparison.RANGE);
        assertThat(ConditionType.START_DATE.floorDirectionFor(PartyRole.CLIENT)).isEqualTo(FloorDirection.MAX);
        assertThat(ConditionType.START_DATE.floorDirectionFor(PartyRole.FREELANCER)).isEqualTo(FloorDirection.MAX);
        // 클라 상한 10/1, 프리 상한 10/21 → 9/15 는 둘 다 이하라 통과
        assertThat(respects(ConditionType.START_DATE, "2026-09-15", "2026-10-01", "2026-10-21")).isTrue();
        // 10/15 는 클라 상한(10/1) 초과 → 거절
        assertThat(respects(ConditionType.START_DATE, "2026-10-15", "2026-10-01", "2026-10-21")).isFalse();
    }

    @Test
    @DisplayName("CHOICE 는 허용한 값이어야 통과한다 — 크기 비교가 성립하지 않는다")
    void choiceTypesCompareByAllowedValues() {
        assertThat(ConditionType.WORK_STYLE.getFloorComparison()).isEqualTo(FloorComparison.CHOICE);
        // 양쪽이 허용한 값이라야 한다. 클라는 상주만, 프리는 재택만 → 접점 없음.
        assertThat(respects(ConditionType.WORK_STYLE, "ONSITE", "ONSITE", "REMOTE")).isFalse();
        assertThat(respects(ConditionType.WORK_STYLE, "ONSITE", "ONSITE", "ANY")).isTrue();

        assertThat(ConditionType.WORK_FORM.getFloorComparison()).isEqualTo(FloorComparison.CHOICE);
        assertThat(respects(ConditionType.WORK_FORM, "PART_TIME", "FULL_TIME", "FULL_TIME")).isFalse();
        assertThat(respects(ConditionType.WORK_FORM, "FULL_TIME", "FULL_TIME", "FULL_TIME")).isTrue();
    }

    @Test
    @DisplayName("NONE 은 비교 기준이 없어 가드가 통과시킨다 — 화면도 안내를 띄우지 않는다")
    void noneTypesHaveNoFloorRule() {
        assertThat(ConditionType.SCOPE.getFloorComparison()).isEqualTo(FloorComparison.NONE);
        assertThat(ConditionType.OTHER.getFloorComparison()).isEqualTo(FloorComparison.NONE);

        // 자유 텍스트라 판정할 기준이 없다. 마지노선이 뭐든 통과한다.
        assertThat(respects(ConditionType.SCOPE, "프론트엔드 전체", "무엇이든", "무엇이든")).isTrue();
        assertThat(respects(ConditionType.OTHER, "임의 텍스트", null, null)).isTrue();
    }

    private boolean respects(ConditionType type, String agreed, String clientFloor, String freelancerFloor) {
        return NegotiationFloorGuard.respectsFloors(type, agreed, clientFloor, freelancerFloor);
    }
}
