package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 대리인이 합의한 값이 <b>양측이 그은 마지노선을 지켰는지</b> 검증한다.
 *
 * <p>필요한 이유: 마지노선은 프롬프트로만 전달되고 LLM 이 지킨다는 보장이 없다. 실제로 프리랜서
 * 마지노선이 480만인데 대리인이 330만을 수락하고, 재택만 허용했는데 상주를 받아들이는 사례를
 * 확인했다. 지시문은 유도일 뿐이라 최종 방어선은 서버에 있어야 한다.
 *
 * <p>검증에 실패하면 락하지 않는다. 조건은 미합의로 남아 사람의 승인 패널로 넘어간다 —
 * {@link NegotiationAgreedValueNormalizer} 가 해석 불가한 값을 강등하는 것과 같은 방식이다.
 *
 * <p><b>마지노선의 방향</b>은 역할마다 반대다.
 * <ul>
 *   <li>프리랜서 = <b>하한</b>. 이 값 미만은 수락하지 않는다(최소 단가·최소 기간·착수 가능일).</li>
 *   <li>클라이언트 = <b>상한</b>. 이 값 초과는 수락하지 않는다(최대 단가·최대 기간·최종 착수 기한).</li>
 *   <li>선택형(근무 방식·형태)은 크기 비교가 없다. 각자 <b>허용한 값</b>이며 {@code ANY} 는 전부 허용이다.</li>
 * </ul>
 */
public final class NegotiationFloorGuard {

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*(MONTH|WEEK)\\s*$", Pattern.CASE_INSENSITIVE);

    /** 기간 비교용 환산. 월/주가 섞여도 순서 비교가 가능하도록 일 단위로 맞춘다. */
    private static final int DAYS_PER_MONTH = 30;
    private static final int DAYS_PER_WEEK = 7;

    private NegotiationFloorGuard() {
    }

    /**
     * 합의값이 양측 마지노선을 지켰는가.
     *
     * <p>마지노선이 {@code null} 인 쪽은 제약이 없는 것으로 본다(아직 제출 전이거나 옛 데이터).
     * 값을 해석할 수 없으면 <b>지키지 못한 것으로 본다</b> — 검증할 수 없는 값을 락하는 것보다
     * 사람에게 넘기는 편이 안전하다.
     *
     * @param type            조건 타입
     * @param agreedValue     대리인이 합의했다고 주장하는 값(정규화된 표기)
     * @param clientFloor     클라이언트 마지노선(상한). 없으면 null
     * @param freelancerFloor 프리랜서 마지노선(하한). 없으면 null
     */
    public static boolean respectsFloors(ConditionType type, String agreedValue,
                                         String clientFloor, String freelancerFloor) {
        if (type == null || agreedValue == null || agreedValue.isBlank()) {
            return false;
        }
        return switch (type) {
            case AMOUNT -> withinRange(parseAmount(agreedValue), parseAmount(clientFloor),
                    parseAmount(freelancerFloor));
            case PERIOD -> withinRange(parsePeriodDays(agreedValue), parsePeriodDays(clientFloor),
                    parsePeriodDays(freelancerFloor));
            case START_DATE -> withinRange(parseEpochDay(agreedValue), parseEpochDay(clientFloor),
                    parseEpochDay(freelancerFloor));
            case WORK_STYLE -> allowedByBoth(agreedValue, clientFloor, freelancerFloor, WorkStyle.ANY.name());
            case WORK_FORM -> allowedByBoth(agreedValue, clientFloor, freelancerFloor, WorkForm.ANY.name());
            // 자유 텍스트는 대소 관계도 허용값도 없다. 검증할 기준이 없으므로 통과시킨다.
            case SCOPE, OTHER -> true;
        };
    }

    /**
     * 하한(프리) ≤ 합의값 ≤ 상한(클라) 인가.
     *
     * <p>합의값 자체를 해석 못 하면 실패다. 마지노선을 해석 못 하는 경우도 실패로 본다 —
     * 그 값은 {@code /start} 에서 이미 정규화를 통과했어야 하므로, 여기서 깨져 있다는 건
     * 신뢰할 수 없는 상태라는 뜻이다.
     */
    private static boolean withinRange(Optional<Long> agreed, Optional<Long> upper, Optional<Long> lower) {
        if (agreed.isEmpty()) {
            return false;
        }
        long value = agreed.get();
        if (upper.isPresent() && value > upper.get()) {
            return false;
        }
        return lower.isEmpty() || value >= lower.get();
    }

    /** 양측이 모두 받아들일 수 있는 선택지인가. 마지노선이 {@code ANY} 면 무엇이든 허용한다. */
    private static boolean allowedByBoth(String agreedValue, String clientFloor, String freelancerFloor,
                                         String anyToken) {
        return allows(clientFloor, agreedValue, anyToken) && allows(freelancerFloor, agreedValue, anyToken);
    }

    private static boolean allows(String floor, String agreedValue, String anyToken) {
        if (floor == null || floor.isBlank()) {
            return true;   // 제약 없음
        }
        String normalized = floor.trim().toUpperCase(Locale.ROOT);
        return normalized.equals(anyToken) || normalized.equals(agreedValue.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 금액 마지노선 파싱. 콤마만 걷어낸 순수 숫자만 인정한다.
     *
     * <p>패키지 공개(package-private)인 이유: 최종 절충값 계산({@link NegotiationCompromiseCalculator})이
     * 같은 규칙으로 두 마지노선을 읽어야 한다. 파싱 규칙이 두 곳에 갈라지면 가드가 통과시킨 값을
     * 절충기가 못 읽는 어긋남이 생긴다 — 한 곳에 둔다.
     */
    static Optional<Long> parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String cleaned = value.replace(",", "").trim();
        return cleaned.matches("\\d+") ? Optional.of(Long.parseLong(cleaned)) : Optional.empty();
    }

    /** 기간 마지노선을 일 단위로 파싱. {@link #parseAmount} 와 같은 이유로 패키지 공개. */
    static Optional<Long> parsePeriodDays(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PERIOD_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long amount = Long.parseLong(matcher.group(1));
        PeriodUnit unit = PeriodUnit.valueOf(matcher.group(2).toUpperCase(Locale.ROOT));
        return Optional.of(amount * (unit == PeriodUnit.WEEK ? DAYS_PER_WEEK : DAYS_PER_MONTH));
    }

    /** 시작일 마지노선을 epoch day 로 파싱. {@link #parseAmount} 와 같은 이유로 패키지 공개. */
    static Optional<Long> parseEpochDay(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()).toEpochDay());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
