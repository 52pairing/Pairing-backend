package com.pairing.contract.domain.model;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * 협상 합의값을 계약서에 넣을 타입으로 바꾼다.
 *
 * <p>협상이 넘겨주는 {@code agreedValue} 는 전부 문자열이고 타입별 포맷이 확정돼 있다(협상 담당 확인, 2026-08-09).
 * <pre>
 *   AMOUNT      "42000000"      원 단위 숫자. 월 단가
 *   START_DATE  "2026-09-01"    ISO LocalDate
 *   PERIOD      "4 MONTH"       숫자 + 공백 + PeriodUnit.name()
 *   WORK_STYLE  "REMOTE"        enum 이름
 *   WORK_FORM   "FULL_TIME"     enum 이름
 *   SCOPE/OTHER 자유 텍스트
 * </pre>
 *
 * <p>AI 제안값도 협상 쪽 정규화를 거친 뒤에만 락되지만, 계약은 5년 남는 문서라 해석 실패를
 * 예외로 만들지 않는다. 값이 깨져 있으면 비운 채로 두고 호출부가 프로젝트 등록값으로 대체한다.
 * 계약 생성이 통째로 막히는 것보다 낫다.
 */
public final class AgreedTerms {

    private static final String PERIOD_DELIMITER = " ";
    private static final int PERIOD_PARTS = 2;

    private final Map<String, String> values;

    private AgreedTerms(Map<String, String> values) {
        this.values = values;
    }

    /** key 는 {@code ConditionType.name()} 이다. 계약이 협상 enum 을 참조하지 않으려고 문자열로 받는다. */
    public static AgreedTerms of(Map<String, String> values) {
        return new AgreedTerms(values == null ? Map.of() : values);
    }

    public Optional<LocalDate> startDate() {
        return raw("START_DATE").flatMap(AgreedTerms::parseDate);
    }

    public Optional<WorkStyle> workStyle() {
        return raw("WORK_STYLE").flatMap(v -> parseEnum(WorkStyle.class, v));
    }

    public Optional<WorkForm> workForm() {
        return raw("WORK_FORM").flatMap(v -> parseEnum(WorkForm.class, v));
    }

    /** "4 MONTH" -&gt; 4개월. 단위까지 함께 돌려줘야 주(week) 계약을 개월로 환산할 수 있다. */
    public Optional<Period> period() {
        return raw("PERIOD").flatMap(AgreedTerms::parsePeriod);
    }

    /** 업무 범위·기타 합의 내용. 계약서 특약사항으로 들어간다. */
    public Optional<String> scope() {
        return raw("SCOPE");
    }

    public Optional<String> other() {
        return raw("OTHER");
    }

    private Optional<String> raw(String type) {
        String value = values.get(type);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<LocalDate> parseDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String value) {
        try {
            return Optional.of(Enum.valueOf(type, value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<Period> parsePeriod(String value) {
        String[] parts = value.split(PERIOD_DELIMITER);
        if (parts.length != PERIOD_PARTS) {
            return Optional.empty();
        }
        try {
            int amount = Integer.parseInt(parts[0]);
            PeriodUnit unit = PeriodUnit.valueOf(parts[1]);
            return amount > 0 ? Optional.of(new Period(amount, unit)) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** 합의된 기간. */
    public record Period(int value, PeriodUnit unit) {
    }
}
