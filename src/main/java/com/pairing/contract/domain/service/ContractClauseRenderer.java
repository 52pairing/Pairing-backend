package com.pairing.contract.domain.service;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractClause;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.model.Deliverables;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 계약서 본문 조항을 만든다. 상세 조회와 PDF 가 같은 문장을 쓴다.
 *
 * <p>문구는 전부 고정이고 값만 갈아 끼운다. AI 를 쓰지 않는다 — 금액·기간·당사자가 한 글자라도
 * 달라지면 그대로 서명되고 분쟁 근거가 된다. AI 가 손대는 것은 제2조 세부 업무 범위와 제15조
 * 특약사항의 <b>원문 요약</b>뿐이며, 그건 계약 생성 시점에 이미 확정돼 들어온다.
 *
 * <p>개월 수는 날짜 차이가 아니라 {@code totalAmount / salaryAmount} 로 구한다. 제4조에 찍히는
 * 두 금액과 개월 수가 서로 어긋나지 않아야 하는데, 날짜로 계산하면 종료일이 하루라도 밀렸을 때
 * "월 500만 × 4개월 = 2,500만" 같은 문장이 나온다.
 */
public final class ContractClauseRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    /** 제6조 요율이 갈리는 계약 금액. 정책 P29·P30 의 1억원 경계. */
    private static final long HIGH_AMOUNT_THRESHOLD = 100_000_000L;

    /** 제12조 시정 기간. 계약 테이블에 없는 양식 고정값이다. */
    private static final int CURE_PERIOD_DAYS = 14;

    private static final String NO_SPECIAL_TERMS = "별도의 특약사항 없음";
    private static final String UNKNOWN_PROJECT = "(프로젝트명 미상)";
    private static final String UNKNOWN_JOB_ROLE = "협의된 직무";
    private static final String REMOTE_LOCATION = "을이 지정하는 장소로 하며, 갑은 별도의 근무 장소를 제공하지 않는다";

    /** 제2조 항 번호. 담당 업무·요구 기술 항이 빠질 수 있어 자리를 고정하지 않는다. */
    private static final String[] MARKS = {"①", "②", "③", "④", "⑤", "⑥"};
    private static final String MARKS_CHARS = String.join("", MARKS);

    private ContractClauseRenderer() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 계약 밖에서 가져와야 하는 값들.
     *
     * @param projectTitle      프로젝트명. 원본이 지워졌으면 null (계약은 5년 보관이라 더 오래 남는다)
     * @param jobRole           계약 대상 직무. 위와 같은 이유로 null 을 허용한다
     * @param skills            포지션 요구 기술. 없거나 원본이 지워졌으면 빈 목록
     * @param draft             AI 가 다듬은 자유 텍스트. 아직 안 채워졌으면(DRAFT) null
     * @param settlementAccount 을의 정산 계좌 한 줄. 미등록이면 null
     */
    public record ClauseContext(String projectTitle, JobRole jobRole, List<SkillCode> skills,
                                ContractDraftText draft, String settlementAccount) {
    }

    /**
     * 계약서 본문을 만든다. 계약이 들고 있지 않은 값만 {@link ClauseContext} 로 받는다.
     *
     * @param context 프로젝트명·직무·AI 문구·정산 계좌. 어느 것이든 없으면 해당 칸이 기본 문구로 채워진다
     */
    public static List<ContractClause> render(Contract contract, ClauseContext context) {
        List<ContractClause> clauses = new ArrayList<>();
        int months = months(contract);

        clauses.add(purpose(context.projectTitle()));
        clauses.add(scope(context));
        clauses.add(period(contract, months));
        clauses.add(amount(contract, months));
        clauses.add(payment(context.settlementAccount()));
        clauses.add(platformFee());
        clauses.add(workCondition(contract));
        clauses.add(inspection(contract));
        clauses.add(intellectualProperty());
        clauses.add(confidentiality(contract));
        clauses.add(amendment());
        clauses.add(termination(contract));
        clauses.add(damages());
        clauses.add(disputeResolution());
        clauses.add(specialTerms(contract));

        return clauses;
    }

    // ==========================================
    // 조항
    // ==========================================

    private static ContractClause purpose(String projectTitle) {
        return new ContractClause(1, "목적",
                "본 계약은 갑이 의뢰하는 「%s」 프로젝트(이하 \"본 프로젝트\")의 수행에 관하여 "
                        .formatted(orDefault(projectTitle, UNKNOWN_PROJECT))
                        + "갑과 을의 권리와 의무를 정함을 목적으로 한다.");
    }

    /**
     * 산출물은 직군별 표준 목록을 쓴다. 프로젝트 등록에도 협상에도 입력란이 없고, 클라이언트가
     * 정하는 값이 아니라 직군이 정해지면 따라오는 관례적 목록이라 상수로 둔다.
     *
     * <p>담당 업무와 세부 업무 범위는 AI 가 줄인 문장이다. 원문이 없으면 AI 도 빈 문자열을
     * 돌려주므로(없는 업무를 지어내지 않는다) 그때는 해당 항을 빼거나 일반 문구로 대체한다.
     */
    private static ContractClause scope(ClauseContext context) {
        JobRole jobRole = context.jobRole();
        ContractDraftText draft = context.draft();

        String label = jobRole == null ? UNKNOWN_JOB_ROLE : jobRole.getLabel();
        List<String> deliverables = jobRole == null
                ? Deliverables.of(null) : Deliverables.of(jobRole.getCategory());

        StringBuilder content = new StringBuilder("① 본 계약의 대상 프로젝트는 「%s」이다."
                .formatted(orDefault(context.projectTitle(), UNKNOWN_PROJECT)));

        content.append(line("%s 을이 수행할 직무는 %s이다.".formatted(mark(content), label)));

        String mainTask = draft == null ? null : draft.mainTaskSummary();
        if (mainTask != null && !mainTask.isBlank()) {
            content.append(line("%s 을이 수행할 주요 업무는 다음과 같다. ".formatted(mark(content)) + mainTask));
        }

        content.append(line("%s 을이 제출할 산출물은 다음과 같다. ".formatted(mark(content))
                + String.join(", ", deliverables)));

        String detailScope = draft == null ? null : draft.detailScopeSummary();
        content.append(line("%s 세부 업무 범위는 ".formatted(mark(content))
                + (detailScope == null || detailScope.isBlank()
                ? "갑이 제공한 과업 내용과 양 당사자가 협상 과정에서 합의한 사항에 따른다."
                : detailScope + "로 한다.")));

        String skills = skillLabels(context.skills());
        if (!skills.isBlank()) {
            content.append(line("%s 요구 기술은 다음과 같다. ".formatted(mark(content)) + skills));
        }

        return new ContractClause(2, "계약 대상 및 업무 범위", content.toString());
    }

    /** 화면에 쓰는 표기 그대로 적는다. enum 이름(SPRING_BOOT)이 계약서에 나가면 안 된다. */
    private static String skillLabels(List<SkillCode> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        return skills.stream().map(SkillCode::getLabel).collect(Collectors.joining(", "));
    }

    /** 항 번호. 담당 업무·요구 기술 항이 빠질 수 있어 고정하지 않고 지금까지 쓴 개수로 매긴다. */
    private static String mark(StringBuilder content) {
        return MARKS[(int) content.chars().filter(c -> MARKS_CHARS.indexOf(c) >= 0).count()];
    }

    private static ContractClause period(Contract contract, int months) {
        return new ContractClause(3, "계약 기간",
                "① 계약 기간은 %s부터 %s까지 %d개월로 한다."
                        .formatted(date(contract.getStartDate()), date(contract.getEndDate()), months)
                        + line("② 계약 기간의 연장은 양 당사자의 서면 합의로 정한다."));
    }

    /**
     * 제4조는 두 줄이다. 월 용역대금과 총 계약 금액.
     *
     * <p>착수금·잔금 분할은 쓰지 않는다. 협상이 합의해 넘겨주는 값이 월 단가 하나뿐이라
     * 나눌 원본이 없다. 플랫폼 수수료는 제6조이며 이것과 별개다.
     */
    private static ContractClause amount(Contract contract, int months) {
        return new ContractClause(4, "계약 금액",
                "① 월 용역대금: %s (부가세 별도)".formatted(money(contract.getSalaryAmount()))
                        + line("② 총 계약 금액: %s (월 용역대금 × %d개월)"
                        .formatted(money(contract.getTotalAmount()), months)));
    }

    /**
     * 지급 주기는 정하지 않는다. 용역비가 플랫폼을 거치지 않아(P29) 플랫폼이 시기를 강제할 근거가
     * 없고, 월 단가 계약이라 나눌 착수금·잔금도 없다.
     */
    private static ContractClause payment(String settlementAccount) {
        String account = settlementAccount == null || settlementAccount.isBlank()
                ? "을이 플랫폼에 등록한 정산 계좌로 한다."
                : settlementAccount + " 로 한다.";

        return new ContractClause(5, "대금 지급",
                "① 용역대금은 갑이 을에게 직접 지급하며, 플랫폼은 그 지급에 관여하지 않는다."
                        + line("② 지급 시기와 방법은 양 당사자가 협의하여 정한다.")
                        + line("③ 지급 계좌는 " + account));
    }

    /** 정책 P29·P30 의 표를 그대로 옮긴다. 실제 부과액은 정산 도메인이 계산한다. */
    private static ContractClause platformFee() {
        return new ContractClause(6, "플랫폼 이용 수수료",
                "① 갑과 을은 페어링 플랫폼 이용에 대하여 다음의 수수료를 각자 부담한다."
                        + line("   - 착수금 수수료: 계약 금액 %s원 미만인 경우 갑 3%%, 을 4%%. "
                        .formatted(comma(HIGH_AMOUNT_THRESHOLD)) + "%s원 이상인 경우 갑 2%%, 을 4%%."
                        .formatted(comma(HIGH_AMOUNT_THRESHOLD)))
                        + line("   - 성공보수 수수료: 계약 금액 %s원 미만인 경우 갑 7%%, 을 6%%. "
                        .formatted(comma(HIGH_AMOUNT_THRESHOLD)) + "%s원 이상인 경우 갑 6%%, 을 6%%."
                        .formatted(comma(HIGH_AMOUNT_THRESHOLD)))
                        + line("② 갑 또는 을의 회원 등급에 따라 위 요율이 인하될 수 있다.")
                        + line("③ 플랫폼 이용 수수료는 제4조의 용역대금과 별개이며, 각 당사자가 "
                        + "페어링에 직접 납부한다.")
                        + line("④ 갑의 착수금 수수료는 프로젝트 등록 시점에, 을의 착수금 수수료는 "
                        + "본 계약의 체결 시점에 발생한다."));
    }

    private static ContractClause workCondition(Contract contract) {
        String location = contract.getWorkLocation() == null || contract.getWorkLocation().isBlank()
                ? REMOTE_LOCATION
                : contract.getWorkLocation();

        return new ContractClause(7, "근무 조건",
                "① 근무 방식: %s".formatted(contract.getWorkStyle().getLabel())
                        + line("② 근무 형태: %s".formatted(contract.getWorkForm().getLabel()))
                        + line("③ 근무 장소: %s".formatted(location)));
    }

    private static ContractClause inspection(Contract contract) {
        int days = contract.getInspectionDays();

        return new ContractClause(8, "검수 및 완료",
                "① 을은 계약 기간이 종료되면 지체 없이 산출물을 갑에게 제출한다."
                        + line("② 갑은 산출물을 수령한 날부터 %d일 이내에 검수를 완료한다.".formatted(days))
                        + line("③ 검수를 통과하면 용역의 완료가 확정된다.")
                        + line("④ 하자가 발생한 경우 갑은 %d일 이내에 서면으로 보완을 요청할 수 있다."
                        .formatted(days)));
    }

    private static ContractClause intellectualProperty() {
        return new ContractClause(9, "지식재산권",
                "① 산출물의 지식재산권은 대금 완납 시 갑에게 귀속된다."
                        + line("② 을은 산출물이 제3자의 권리를 침해하지 않음을 보증한다."));
    }

    private static ContractClause confidentiality(Contract contract) {
        return new ContractClause(10, "비밀유지",
                "① 양 당사자는 계약 수행 중 알게 된 상대방의 비밀정보를 제3자에게 누설하지 않는다."
                        + line("② 비밀유지 의무는 계약 종료 후 %d년간 유효하다."
                        .formatted(contract.getConfidentialYears())));
    }

    private static ContractClause amendment() {
        return new ContractClause(11, "계약 변경",
                "계약 변경은 양 당사자의 서면(전자) 합의로만 가능하다.");
    }

    /**
     * 파기 위약금은 파기 주체가 상대방과 플랫폼에 각각 {@code penaltyRate}%씩 부담한다(정책 P32).
     * 수행분 정산은 이 위약금과 별개다.
     */
    private static ContractClause termination(Contract contract) {
        String rate = rate(contract.getPenaltyRate());

        return new ContractClause(12, "계약 해지 및 위약금",
                "① 일방이 계약을 위반하고 %d일 이내에 시정하지 않는 경우 상대방은 계약을 해지할 수 있다."
                        .formatted(CURE_PERIOD_DAYS)
                        + line("② 을이 파기하는 경우: 수행분을 정산한 뒤 총 계약 금액의 %s%%를 상대방에게, "
                        .formatted(rate) + "%s%%를 플랫폼에 위약금으로 지급한다.".formatted(rate))
                        + line("③ 갑이 파기하는 경우: 수행분을 지급한 뒤 총 계약 금액의 %s%%를 상대방에게, "
                        .formatted(rate) + "%s%%를 플랫폼에 위약금으로 지급한다.".formatted(rate)));
    }

    private static ContractClause damages() {
        return new ContractClause(13, "손해배상",
                "계약 위반으로 손해가 발생한 경우 귀책 당사자가 배상한다.");
    }

    private static ContractClause disputeResolution() {
        return new ContractClause(14, "분쟁 해결",
                "① 준거법: 대한민국 법률을 적용한다."
                        + line("② 관할: 민사소송법상의 관할 법원으로 한다.")
                        + line("③ 플랫폼의 분쟁 조정 절차를 우선 적용한다."));
    }

    private static ContractClause specialTerms(Contract contract) {
        String terms = contract.getSpecialTerms();
        return new ContractClause(15, "특약사항",
                terms == null || terms.isBlank() ? NO_SPECIAL_TERMS : terms);
    }

    // ==========================================
    // 표기
    // ==========================================

    /**
     * 계약 개월 수. 제4조의 두 금액과 반드시 맞아야 한다.
     *
     * <p>{@code totalAmount = salaryAmount × months} 로 만들어졌으므로 나누면 정확히 떨어진다.
     * 0으로 나누는 것만 막고, 그 경우 기간을 표시하지 않도록 1을 돌려준다.
     */
    private static int months(Contract contract) {
        Long salary = contract.getSalaryAmount();
        Long total = contract.getTotalAmount();

        if (salary == null || salary <= 0 || total == null || total <= 0) {
            return 1;
        }
        return Math.max(1, (int) (total / salary));
    }

    private static String money(Long amount) {
        return amount == null ? "-" : comma(amount) + "원";
    }

    private static String comma(long amount) {
        return String.format("%,d", amount);
    }

    /** 위약금율. {@code numeric(5,2)} 라 10.00 으로 오는데 계약서에는 "10%" 로 찍는다. */
    private static String rate(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "-" : value.format(DATE);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 조항 안의 항 구분. PDF 와 화면이 같은 줄바꿈을 쓰도록 한 곳에서만 만든다. */
    private static String line(String text) {
        return System.lineSeparator() + text;
    }
}
