package com.pairing.negotiation.domain.service;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.negotiation.domain.model.ConditionType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 대리인이 제안한 값을 계약이 쓰는 표기로 정규화·검증한다.
 *
 * <p>필요한 이유: LLM 은 시스템에 없는 값을 지어내거나(예: WORK_STYLE 에 {@code HYBRID}) 단위를 빼먹는다
 * (예: PERIOD 에 {@code "3 MONTH"} 대신 {@code "3"}). 이 값이 그대로 락되면 계약 단계에서 해석할 수 없으므로,
 * 락 직전에 한 번 걸러 낸다. 프롬프트로도 유도하지만 LLM 출력은 보장되지 않으므로 최종 방어선은 여기다.
 *
 * <p>표기 기준은 {@link NegotiationConditionCalculator} 가 자동 생성하는 조건값과 동일하다.
 * AMOUNT={@code "42000000"}, PERIOD={@code "4 MONTH"}, START_DATE={@code "2026-09-01"},
 * WORK_STYLE/WORK_FORM=enum 이름, SCOPE/OTHER=자유 텍스트.
 */
public final class NegotiationAgreedValueNormalizer {

    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*(MONTH|WEEK|개월|주)?\\s*$", Pattern.CASE_INSENSITIVE);

    private NegotiationAgreedValueNormalizer() {
    }

    /**
     * 제안값을 계약 표기로 정규화한다.
     *
     * @param type      조건 타입
     * @param rawValue  AI 가 제안한 값
     * @param reference 같은 조건의 기존 값(희망값 등). PERIOD 단위를 잃었을 때 복원하는 데 쓴다.
     * @return 정규화된 값. 해석할 수 없으면 {@link Optional#empty()} (→ 락하지 않는다)
     */
    public static Optional<String> normalize(ConditionType type, String rawValue, String reference) {
        if (type == null || rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String value = rawValue.trim();
        return switch (type) {
            case AMOUNT -> normalizeAmount(value);
            case PERIOD -> normalizePeriod(value, reference);
            case START_DATE -> normalizeDate(value);
            case WORK_STYLE -> normalizeEnum(value, WorkStyle.class);
            case WORK_FORM -> normalizeEnum(value, WorkForm.class);
            case SCOPE, OTHER -> Optional.of(value);
        };
    }

    /** 선택형 쟁점의 허용값. AI 에게 후보를 알려줘 없는 값을 만들지 않게 한다. 자유 입력이면 빈 목록. */
    public static List<String> allowedValues(ConditionType type) {
        if (type == null) {
            return List.of();
        }
        return switch (type) {
            case WORK_STYLE -> names(WorkStyle.values());
            case WORK_FORM -> names(WorkForm.values());
            default -> List.of();
        };
    }

    /** 형식이 정해진 쟁점의 표기법 힌트. 자유 텍스트면 null. */
    public static String valueFormat(ConditionType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case AMOUNT -> "숫자만(단위·콤마 없이, 원 단위)";
            case PERIOD -> "<숫자> MONTH";
            case START_DATE -> "YYYY-MM-DD";
            default -> null;
        };
    }

    /** "4,200만" 같은 표기는 해석하지 않는다. 콤마·통화기호만 걷어내고 순수 숫자만 인정. */
    private static Optional<String> normalizeAmount(String value) {
        String cleaned = value.replace(",", "").replace("원", "").replace("₩", "").trim();
        if (!cleaned.matches("\\d+")) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(Long.parseLong(cleaned)));
    }

    /** 단위가 빠지면(LLM 이 흔히 그런다) 기존 값의 단위로 복원하고, 그것도 없으면 MONTH 로 본다. */
    private static Optional<String> normalizePeriod(String value, String reference) {
        Matcher matcher = PERIOD_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int amount = Integer.parseInt(matcher.group(1));
        PeriodUnit unit = toPeriodUnit(matcher.group(2))
                .or(() -> referenceUnit(reference))
                .orElse(PeriodUnit.MONTH);
        return Optional.of(amount + " " + unit.name());
    }

    private static Optional<PeriodUnit> toPeriodUnit(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MONTH", "개월" -> Optional.of(PeriodUnit.MONTH);
            case "WEEK", "주" -> Optional.of(PeriodUnit.WEEK);
            default -> Optional.empty();
        };
    }

    private static Optional<PeriodUnit> referenceUnit(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PERIOD_PATTERN.matcher(reference.trim());
        return matcher.matches() ? toPeriodUnit(matcher.group(2)) : Optional.empty();
    }

    private static Optional<String> normalizeDate(String value) {
        try {
            return Optional.of(LocalDate.parse(value).toString());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> Optional<String> normalizeEnum(String value, Class<E> enumType) {
        String candidate = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name)
                .filter(name -> name.equals(candidate))
                .findFirst();
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
