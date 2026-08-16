package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 최종 절충안(Final Compromise Offer)의 절충값을 계산한다 = <b>양쪽이 각자 마지노선을 넘겨 만나는
 * 중간 지점</b>. 라운드 상한(15회)까지 합의에 이르지 못했을 때 미합의 조건마다 한 번 계산한다.
 *
 * <p>마지노선 파싱은 {@link NegotiationFloorGuard} 의 것을 재사용한다 — 가드가 통과시킨 값을
 * 절충기가 못 읽는 어긋남을 막기 위해 파싱 규칙을 한 곳에 둔다.
 *
 * <p><b>마지노선의 방향</b>은 {@link NegotiationFloorGuard}·{@link ConditionType#floorDirectionFor} 와
 * 같다: 금액·기간은 프리=하한/클라=상한, 시작일은 양측 상한(+프리 가용 시작일 하한), 선택형은 허용값.
 */
public final class NegotiationCompromiseCalculator {

    /** 기간 절충값을 월로 환산할 때 쓰는 기준(가드의 일 환산과 동일). */
    private static final int DAYS_PER_MONTH = 30;

    private NegotiationCompromiseCalculator() {
    }

    /**
     * 양쪽이 각자 마지노선을 넘겨 만나는 최종 절충값.
     *
     * <ul>
     *   <li>금액·기간: 두 마지노선의 <b>중간값</b>. 예) 프리 460·클라 340 → 400.
     *       기간은 일 단위 중간을 월로 환산(최소 1개월).</li>
     *   <li>시작일: 프리 <b>가용 시작일</b>(클라가 그날까지 당겨 맞춤). 프리가 그날 이후로도 못 기다리면 절충 불가.</li>
     *   <li>WORK_STYLE: {@code ANY}(혼합/하이브리드).</li>
     *   <li>WORK_FORM: {@code ANY}(모두 가능). 실제 근무형태를 확정 않는 유연 합의라 다소 약하지만
     *       WORK_STYLE 과 대칭으로 통일한다.</li>
     *   <li>SCOPE / OTHER(자유 텍스트): 절충 기준이 없어 <b>절충 불가</b>({@link Optional#empty()}).</li>
     * </ul>
     *
     * <p>어느 한쪽 마지노선이 비었거나 해석 불가한 RANGE 는 절충값을 낼 수 없어 empty 다 —
     * 호출부는 이 경우를 "절충 불가"로 보고 즉시 결렬시킨다.
     */
    public static Optional<String> compromise(ConditionType type, String clientFloor, String freelancerFloor) {
        return compromise(type, clientFloor, freelancerFloor, null);
    }

    /**
     * 최종 절충값. {@code freelancerValue} 는 START_DATE 에서 프리랜서 <b>가용 시작일</b>로만 쓴다
     * (다른 타입은 무시). 시작일은 양측 마지노선이 모두 상한이라 중간값이 성립하지 않기 때문이다.
     */
    public static Optional<String> compromise(ConditionType type, String clientFloor, String freelancerFloor,
                                              String freelancerValue) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case AMOUNT -> midAmount(clientFloor, freelancerFloor);
            case PERIOD -> midPeriodMonths(clientFloor, freelancerFloor);
            // 시작일: 프리는 가용 시작일보다 이르게 시작 못 하므로, 절충 = 그 가용 시작일(클라가 그날까지
            // 당겨 맞춘다). 단 프리가 그날 이후로도 못 기다리면(프리 상한 < 가용일) 성립 불가.
            case START_DATE -> compromiseDate(freelancerValue, freelancerFloor);
            case WORK_STYLE -> Optional.of(WorkStyle.ANY.name());
            case WORK_FORM -> Optional.of(WorkForm.ANY.name());
            case SCOPE, OTHER -> Optional.empty();
        };
    }

    private static Optional<String> midAmount(String clientFloor, String freelancerFloor) {
        Optional<Long> upper = NegotiationFloorGuard.parseAmount(clientFloor);
        Optional<Long> lower = NegotiationFloorGuard.parseAmount(freelancerFloor);
        if (upper.isEmpty() || lower.isEmpty()) {
            return Optional.empty();
        }
        // 두 큰 값을 더해도 long 범위 안이다(월 단가라 억 단위). 오버플로 걱정 없음.
        return Optional.of(Long.toString((upper.get() + lower.get()) / 2));
    }

    private static Optional<String> midPeriodMonths(String clientFloor, String freelancerFloor) {
        Optional<Long> upper = NegotiationFloorGuard.parsePeriodDays(clientFloor);
        Optional<Long> lower = NegotiationFloorGuard.parsePeriodDays(freelancerFloor);
        if (upper.isEmpty() || lower.isEmpty()) {
            return Optional.empty();
        }
        long midDays = (upper.get() + lower.get()) / 2;
        // 계약 표기는 월 단위가 표준(NegotiationConditionCalculator). 반올림하되 0개월은 없다.
        long months = Math.max(1, Math.round(midDays / (double) DAYS_PER_MONTH));
        return Optional.of(months + " MONTH");
    }

    private static Optional<String> compromiseDate(String freelancerAvailableFrom, String freelancerFloor) {
        Optional<Long> earliest = NegotiationFloorGuard.parseEpochDay(freelancerAvailableFrom);
        if (earliest.isEmpty()) {
            return Optional.empty();
        }
        Optional<Long> freelancerMax = NegotiationFloorGuard.parseEpochDay(freelancerFloor);
        if (freelancerMax.isPresent() && earliest.get() > freelancerMax.get()) {
            return Optional.empty();   // 프리가 가용일 이후로도 못 기다린다 → 절충 불가
        }
        return Optional.of(LocalDate.ofEpochDay(earliest.get()).toString());
    }
}
