package com.pairing.project.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 프로젝트. 모집 직군(Position)을 포함하는 애그리거트 루트다.
 *
 * <p>clientId 는 {@code client_profile.id} 다. {@code account.id} 가 아니다.
 * 변환은 application 계층이 담당한다.
 *
 * <p>모집 인원과 포지션 추가·삭제는 착수금 결제 전(REGISTERED)까지만 가능하다.
 * 결제 후에는 직군·직무·경력·요구 스킬만 바꿀 수 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    private static final int RECRUIT_WEEKS = 2;
    private static final int EXTENSION_WEEKS = 1;
    private static final int MAX_EXTENSION = 2;

    private static final int MAX_TITLE = 200;
    private static final int MAX_TEXT = 1500;
    private static final int MAX_POSITIONS = 100;
    private static final int MAX_FILES = 10;
    private static final long MIN_BUDGET = 5_000_000L;
    private static final long MAX_BUDGET = 1_000_000_000L;
    private static final int MAX_PERIOD = 24;

    private Long id;
    private Long clientId;
    private String title;

    private LocalDate startDesiredDate;
    private boolean startNegotiable;
    private int periodValue;
    private PeriodUnit periodUnit;
    private Long budgetAmount;
    private WorkStyle workStyle;
    private WorkForm workForm;
    private String workLocation;

    private String currentSituation;
    private String mainTask;
    private String detailScope;
    private String extraNote;

    private ProjectStatus status;
    private ProjectPaymentStatus paymentStatus;

    private int totalHeadcount;
    private int confirmedHeadcount;

    private LocalDateTime recruitStartedAt;
    private LocalDateTime recruitDeadline;
    private int extensionCount;
    private int freeRerecommendUsed;
    private int paidRerecommendUsed;

    private LocalDateTime noticeAgreedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime closedAt;
    private LocalDate retentionUntil;
    private LocalDateTime createdAt;

    private List<Position> positions;
    private List<Long> fileIds;

    private Project(Long id, Long clientId, String title, LocalDate startDesiredDate,
                    boolean startNegotiable, int periodValue, PeriodUnit periodUnit, Long budgetAmount,
                    WorkStyle workStyle, WorkForm workForm, String workLocation,
                    String currentSituation, String mainTask, String detailScope, String extraNote,
                    ProjectStatus status, ProjectPaymentStatus paymentStatus,
                    int totalHeadcount, int confirmedHeadcount,
                    LocalDateTime recruitStartedAt, LocalDateTime recruitDeadline, int extensionCount,
                    int freeRerecommendUsed, int paidRerecommendUsed,
                    LocalDateTime noticeAgreedAt, LocalDateTime canceledAt, LocalDateTime closedAt,
                    LocalDate retentionUntil, LocalDateTime createdAt,
                    List<Position> positions, List<Long> fileIds) {
        this.id = id;
        this.clientId = clientId;
        this.title = title;
        this.startDesiredDate = startDesiredDate;
        this.startNegotiable = startNegotiable;
        this.periodValue = periodValue;
        this.periodUnit = periodUnit;
        this.budgetAmount = budgetAmount;
        this.workStyle = workStyle;
        this.workForm = workForm;
        this.workLocation = workLocation;
        this.currentSituation = currentSituation;
        this.mainTask = mainTask;
        this.detailScope = detailScope;
        this.extraNote = extraNote;
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.totalHeadcount = totalHeadcount;
        this.confirmedHeadcount = confirmedHeadcount;
        this.recruitStartedAt = recruitStartedAt;
        this.recruitDeadline = recruitDeadline;
        this.extensionCount = extensionCount;
        this.freeRerecommendUsed = freeRerecommendUsed;
        this.paidRerecommendUsed = paidRerecommendUsed;
        this.noticeAgreedAt = noticeAgreedAt;
        this.canceledAt = canceledAt;
        this.closedAt = closedAt;
        this.retentionUntil = retentionUntil;
        this.createdAt = createdAt;
        this.positions = positions;
        this.fileIds = fileIds;
    }

    // ==========================================
    // 생성 · 복원
    // ==========================================

    /**
     * 등록. 상태는 REGISTERED, 결제 상태는 DEPOSIT_PENDING 으로 시작한다.
     *
     * <p>workLocation 은 상주(ONSITE)일 때만 채운다. 재택·모두 가능이면 무시한다.
     * 등록 전 안내 동의는 컨트롤러가 이미 검증했으므로 여기서 동의 시각을 남긴다.
     *
     * <p>포지션은 {@link #update} 와 같이 {@link PositionUpdate} 로 받는다. 두 경로 모두
     * positionNo 를 1부터 다시 매기고, 포지션 생성은 애그리거트 안에서만 일어난다.
     */
    public static Project create(Long clientId, String title, LocalDate startDesiredDate,
                                 boolean startNegotiable, int periodValue, PeriodUnit periodUnit,
                                 Long budgetAmount, WorkStyle workStyle, WorkForm workForm,
                                 String clientAddress, String currentSituation, String mainTask,
                                 String detailScope, String extraNote,
                                 List<PositionUpdate> positionUpdates, List<Long> fileIds) {

        List<PositionUpdate> safeUpdates = positionUpdates == null ? List.of() : positionUpdates;
        List<Long> safeFileIds = fileIds == null ? List.of() : fileIds;

        List<Position> positions = new ArrayList<>();
        int no = 1;
        for (PositionUpdate u : safeUpdates) {
            positions.add(Position.create(no++, u.jobCategory(), u.jobRole(),
                    u.minCareerYears(), u.headcount(), u.skills()));
        }

        validate(clientId, title, startDesiredDate, periodValue, periodUnit, budgetAmount,
                workStyle, workForm, currentSituation, mainTask, detailScope, extraNote,
                positions, safeFileIds);

        LocalDateTime now = LocalDateTime.now();
        return new Project(null, clientId, title, startDesiredDate, startNegotiable,
                periodValue, periodUnit, budgetAmount, workStyle, workForm,
                workStyle == WorkStyle.ONSITE ? clientAddress : null,
                currentSituation, mainTask, detailScope, extraNote,
                ProjectStatus.REGISTERED, ProjectPaymentStatus.DEPOSIT_PENDING,
                sumHeadcount(positions), 0,
                null, null, 0, 0, 0,
                now, null, null, null, null,
                new ArrayList<>(positions), new ArrayList<>(safeFileIds));
    }

    public static Project reconstitute(Long id, Long clientId, String title, LocalDate startDesiredDate,
                                       boolean startNegotiable, int periodValue, PeriodUnit periodUnit,
                                       Long budgetAmount, WorkStyle workStyle, WorkForm workForm,
                                       String workLocation, String currentSituation, String mainTask,
                                       String detailScope, String extraNote,
                                       ProjectStatus status, ProjectPaymentStatus paymentStatus,
                                       int totalHeadcount, int confirmedHeadcount,
                                       LocalDateTime recruitStartedAt, LocalDateTime recruitDeadline,
                                       int extensionCount, int freeRerecommendUsed, int paidRerecommendUsed,
                                       LocalDateTime noticeAgreedAt, LocalDateTime canceledAt,
                                       LocalDateTime closedAt, LocalDate retentionUntil,
                                       LocalDateTime createdAt,
                                       List<Position> positions, List<Long> fileIds) {
        return new Project(id, clientId, title, startDesiredDate, startNegotiable, periodValue, periodUnit,
                budgetAmount, workStyle, workForm, workLocation, currentSituation, mainTask,
                detailScope, extraNote, status, paymentStatus, totalHeadcount, confirmedHeadcount,
                recruitStartedAt, recruitDeadline, extensionCount, freeRerecommendUsed, paidRerecommendUsed,
                noticeAgreedAt, canceledAt, closedAt, retentionUntil, createdAt,
                new ArrayList<>(positions), new ArrayList<>(fileIds));
    }

    // ==========================================
    // 수정
    // ==========================================

    /** 등록 시 입력한 값을 모두 바꾼다. 진행 중인 매칭에는 반영되지 않는다(스냅샷 기준). */
    public void update(String title, LocalDate startDesiredDate, boolean startNegotiable,
                       int periodValue, PeriodUnit periodUnit, Long budgetAmount,
                       WorkStyle workStyle, WorkForm workForm, String clientAddress,
                       String currentSituation, String mainTask, String detailScope, String extraNote,
                       List<PositionUpdate> positionUpdates, List<Long> fileIds) {

        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(ProjectErrorCode.INVALID_STATUS);
        }
        // 착수금 수수료가 등록 시점 예산으로 이미 확정·결제됐다. 예산을 바꾸면 낸 금액과 어긋난다.
        if (!isHeadcountChangeable() && !Objects.equals(this.budgetAmount, budgetAmount)) {
            throw new BusinessException(ProjectErrorCode.BUDGET_NOT_CHANGEABLE);
        }

        List<Long> safeFileIds = fileIds == null ? List.of() : fileIds;
        validateBasic(title, startDesiredDate, periodValue, periodUnit, budgetAmount,
                workStyle, workForm, currentSituation, mainTask, detailScope, extraNote, safeFileIds);

        this.title = title;
        this.startDesiredDate = startDesiredDate;
        this.startNegotiable = startNegotiable;
        this.periodValue = periodValue;
        this.periodUnit = periodUnit;
        this.budgetAmount = budgetAmount;
        this.workStyle = workStyle;
        this.workForm = workForm;
        this.workLocation = workStyle == WorkStyle.ONSITE ? clientAddress : null;
        this.currentSituation = currentSituation;
        this.mainTask = mainTask;
        this.detailScope = detailScope;
        this.extraNote = extraNote;
        this.fileIds = new ArrayList<>(safeFileIds);

        applyPositions(positionUpdates);
        this.totalHeadcount = sumHeadcount(this.positions);
    }

    /**
     * 포지션 추가·수정·삭제를 한 번에 반영한다.
     *
     * <p>positionId 가 있으면 기존 포지션 수정, null 이면 추가, 요청에 없는 기존 포지션은 삭제한다.
     * 착수금 결제 후에는 추가·삭제와 인원 변경을 막는다.
     */
    private void applyPositions(List<PositionUpdate> updates) {
        if (updates == null || updates.isEmpty() || updates.size() > MAX_POSITIONS) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }

        boolean changeable = isHeadcountChangeable();
        Map<Long, Position> current = positions.stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Position::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        List<Position> result = new ArrayList<>();
        int no = 1;

        // 기존 포지션도 요청 순서대로 번호를 다시 매긴다. 새 포지션을 앞에 끼우면
        // 기존 번호와 겹쳐 uk_project_position 을 위반하기 때문이다.
        for (PositionUpdate u : updates) {
            if (u.positionId() == null) {
                if (!changeable) {
                    throw new BusinessException(ProjectErrorCode.POSITION_NOT_CHANGEABLE);
                }
                result.add(Position.create(no++, u.jobCategory(), u.jobRole(),
                        u.minCareerYears(), u.headcount(), u.skills()));
                continue;
            }

            Position existing = current.remove(u.positionId());
            if (existing == null) {
                throw new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND);
            }
            if (!changeable && existing.getHeadcount() != u.headcount()) {
                throw new BusinessException(ProjectErrorCode.HEADCOUNT_NOT_CHANGEABLE);
            }
            existing.changeCondition(u.jobCategory(), u.jobRole(),
                    u.minCareerYears(), u.headcount(), u.skills());
            existing.renumber(no++);
            result.add(existing);
        }

        // 요청에 없는 기존 포지션 = 삭제 대상
        if (!current.isEmpty() && !changeable) {
            throw new BusinessException(ProjectErrorCode.POSITION_NOT_CHANGEABLE);
        }

        this.positions = result;
    }

    // ==========================================
    // 상태 전이
    // ==========================================

    /** 착수금 결제 완료. 여기서부터 모집이 시작된다. */
    public void startRecruiting() {
        requireStatus(ProjectStatus.REGISTERED);
        this.status = ProjectStatus.RECRUITING;
        this.paymentStatus = ProjectPaymentStatus.DEPOSIT_PAID;
        this.recruitStartedAt = LocalDateTime.now();
        this.recruitDeadline = this.recruitStartedAt.plusWeeks(RECRUIT_WEEKS);
    }

    /**
     * 정상 흐름을 목표 단계까지 민다.
     *
     * <p>여러 명을 모집하면 같은 전이가 인원 수만큼 들어온다. 이미 같거나 앞선 단계면 조용히
     * 넘어간다. 1명이 계약 대기까지 갔는데 2번째가 그제야 수락한다고 협상중으로 되돌리면 안 된다.
     * 프로젝트는 가장 앞선 단계를 대표로 표시한다. (요구사항 R29)
     *
     * <p>반대로 취소·종료된 프로젝트는 예외로 알린다. 조용히 넘기면 호출한 도메인이 성공으로
     * 알고 죽은 프로젝트에 협상·계약을 붙인다.
     */
    private void advanceTo(ProjectStatus target) {
        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(ProjectErrorCode.PROJECT_ALREADY_CLOSED);
        }
        if (status.isBefore(target)) {
            this.status = target;
        }
    }

    /**
     * 협상 시작. 프리랜서가 매칭 요청을 수락하면 매칭 도메인이 호출한다. (요구사항 1227)
     *
     * <p>매칭 요청을 <b>보낸</b> 시점은 아직 모집중이다. 후보 추천·요청 발송·응답 대기·재추천이
     * 전부 모집중에 들어간다. 수락이 있어야 조건 조율이 시작된다.
     */
    public void startNegotiating() {
        advanceTo(ProjectStatus.NEGOTIATING);
    }

    /** 계약 대기. 협상이 끝나 계약서가 만들어지면 계약 도메인이 호출한다. (요구사항 1233) */
    public void awaitContract() {
        advanceTo(ProjectStatus.CONTRACT_PENDING);
    }

    /**
     * 인원별 진행 단계에 맞춰 대표 상태를 다시 맞춘다. 협상 결렬·거절·기한 만료 뒤 매칭이 호출한다.
     *
     * <p>{@link #advanceTo} 와 달리 뒤로도 간다. 협상하던 사람이 전부 빠지면 다시 후보를 찾아야 하고,
     * 요구사항 1221 은 "거절 후 재추천"을 모집중에 넣고 있다. 응답 대기만 남은 경우도 모집중이다.
     *
     * <p>인원별 상태는 매칭만 안다. 세는 일은 호출부가 하고, 어느 상태가 되는지는 여기서 정한다.
     *
     * <p>진행중 이후로는 손대지 않는다. 전원 확정으로 프로젝트가 실제 시작됐기 때문이다.
     * 취소·종료된 프로젝트도 조용히 넘어간다. 취소하면서 매칭 요청을 정리할 때 이 호출이 따라올 수
     * 있는데, 여기서 예외를 던지면 그 정리가 통째로 롤백된다.
     *
     * @param hasContractPending 계약 대기 이상인 요청이 하나라도 있는가
     * @param hasNegotiating     수락·협상중인 요청이 하나라도 있는가
     */
    public void syncStage(boolean hasContractPending, boolean hasNegotiating) {
        if (status != ProjectStatus.NEGOTIATING && status != ProjectStatus.CONTRACT_PENDING) {
            return;
        }
        if (hasContractPending) {
            this.status = ProjectStatus.CONTRACT_PENDING;
        } else if (hasNegotiating) {
            this.status = ProjectStatus.NEGOTIATING;
        } else {
            this.status = ProjectStatus.RECRUITING;
        }
    }

    /**
     * 인원 1명 확정. 양측 서명이 끝나면 계약 도메인이 호출한다.
     *
     * <p>여기서는 진행중으로 넘기지 않는다. 서명만으로는 부족하고 프리랜서 착수금 수수료까지
     * 결제돼야 하는데(P27), 그 시점은 정산 도메인만 안다. 전환은 {@link #startProgress} 가 한다.
     */
    public void confirmPosition(Long positionId) {
        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(ProjectErrorCode.PROJECT_ALREADY_CLOSED);
        }
        Position target = positions.stream()
                .filter(p -> Objects.equals(p.getId(), positionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.POSITION_NOT_FOUND));

        target.confirm();
        this.confirmedHeadcount = positions.stream().mapToInt(Position::getConfirmedCount).sum();
    }

    /** 모집 인원이 모두 계약으로 확정됐는가. 진행중 전환의 한쪽 조건이다. */
    public boolean isFullyStaffed() {
        return positions.stream().allMatch(Position::isFilled);
    }

    /**
     * 진행중 전환. 필요 인원이 모두 확정되고 프리랜서 착수금 수수료까지 결제되면 넘어간다.
     *
     * <p>결제 완료를 계기로 정산 도메인이 호출한다. 인원이 덜 찼으면 아무 일도 하지 않는다 —
     * 3명 중 2명만 계약한 상태에서 그 2명이 수수료를 내도 프로젝트는 계약 대기에 머문다.
     *
     * @return 이번 호출로 실제 넘어갔으면 true
     */
    public boolean startProgress() {
        if (!isFullyStaffed() || !status.isBefore(ProjectStatus.IN_PROGRESS)) {
            return false;
        }
        advanceTo(ProjectStatus.IN_PROGRESS);
        return true;
    }

    /** 1주 단위, 최대 2회. 상한을 넘겨 중단하면 클라이언트 파기로 본다. */
    public void extendRecruit() {
        requireStatus(ProjectStatus.RECRUITING);
        if (extensionCount >= MAX_EXTENSION) {
            throw new BusinessException(ProjectErrorCode.EXTENSION_LIMIT_EXCEEDED);
        }
        this.extensionCount++;
        this.recruitDeadline = this.recruitDeadline.plusWeeks(EXTENSION_WEEKS);
    }

    /**
     * 남은 기간과 무관하게 모집을 닫는다. 프로젝트는 취소됨으로 넘어간다.
     *
     * <p>요구사항의 상태 정의에서 [취소됨] 의 예시가 "클라이언트가 모집을 종료했습니다" 다.
     * 모집을 닫는다는 것은 더 이상 모집·협상·계약을 진행하지 않겠다는 뜻으로 본다.
     *
     * <p>진행 중인 협상이 있는 채로 닫으면 그 협상이 갈 곳이 없어진다. 그 판정은 협상 도메인만
     * 할 수 있어 여기서는 막지 못한다. 호출부가 먼저 확인해야 한다.
     */
    public void closeRecruit(LocalDate retentionUntil) {
        // 범용 PJ_006 대신 전용 코드를 쓴다. 화면에서 왜 안 되는지 그대로 보여줄 수 있어야 한다.
        if (status != ProjectStatus.RECRUITING) {
            throw new BusinessException(ProjectErrorCode.RECRUIT_CLOSE_NOT_ALLOWED);
        }
        positions.stream().filter(p -> !p.isFilled()).forEach(Position::close);
        this.recruitDeadline = LocalDateTime.now();
        this.status = ProjectStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.retentionUntil = retentionUntil;
    }

    /**
     * 모집 기간 만료로 인한 취소. (정책 P46)
     *
     * <p>마감이 지났는데 아직 모집 중이면 필요한 인원이 확정되지 않은 것이다.
     * 인원이 다 찼다면 계약 도메인이 이미 진행중으로 넘겼을 것이기 때문이다. (P47)
     *
     * <p>클라이언트가 누른 모집 종료와 결과는 같지만 원인이 다르다. 연장을 다 쓰고도 만료된 경우는
     * 파기 판정이라 위약금 대상이고, 연장하지 않고 기본 2주가 지난 경우는 대상이 아니다.
     * 그 구분은 {@code extensionCount} 로 나중에 판정한다.
     *
     * <p>{@code recruitDeadline} 은 덮어쓰지 않는다. 언제 만료됐는지가 위약금 산정 근거가 된다.
     */
    public void expireRecruit(LocalDate retentionUntil) {
        requireStatus(ProjectStatus.RECRUITING);
        positions.stream().filter(p -> !p.isFilled()).forEach(Position::close);
        this.status = ProjectStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.retentionUntil = retentionUntil;
    }

    /** 연장 기회를 다 쓰고도 만료됐는가. 파기 판정과 위약금 대상 여부를 가른다. (P46) */
    public boolean isExtensionExhausted() {
        return this.extensionCount >= MAX_EXTENSION;
    }

    /**
     * 등록 취소. 잘못 등록한 프로젝트를 내린다.
     *
     * <p>착수금 결제 전에만 가능하다. 결제하면 모집이 시작되고 매칭이 돌기 시작해서,
     * 그 뒤로는 모집 종료로만 닫을 수 있다.
     *
     * <p>행은 지우지 않는다. 취소됨으로 남겨 목록의 [취소됨] 탭에서 볼 수 있게 한다.
     *
     * <p>미결제 착수금 정산을 함께 취소하는 것은 호출부가 한다. 정산은 다른 도메인이다.
     */
    public void cancelRegistration(LocalDate retentionUntil) {
        if (status != ProjectStatus.REGISTERED) {
            throw new BusinessException(ProjectErrorCode.REGISTRATION_CANCEL_NOT_ALLOWED);
        }
        positions.forEach(Position::close);
        this.status = ProjectStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.retentionUntil = retentionUntil;
    }

    /**
     * 완료 처리. 진행중 -> 완료 대기. (정책 P30)
     *
     * <p>여기서 끝이 아니다. 성공보수 수수료 결제 버튼이 열릴 뿐이고, 그 결제까지 끝나야
     * {@link #close} 로 종료가 된다.
     *
     * <p>진행중이 아니면 PJ_006. 포지션은 아직 닫지 않는다. 결제가 남아 있어 되돌아올 여지가 있다.
     */
    public void requestCompletion() {
        requireStatus(ProjectStatus.IN_PROGRESS);
        this.status = ProjectStatus.COMPLETION_PENDING;
        this.paymentStatus = ProjectPaymentStatus.SUCCESS_FEE_PENDING;
    }

    /**
     * 성공보수 결제 완료. 완료 대기 -> 종료. (정책 P30)
     *
     * <p>완료 대기가 아니면 PJ_006. 일이 끝났으므로 남아 있는 포지션도 함께 닫는다.
     * 리뷰 작성은 이 상태부터 열린다. (P51)
     */
    public void close(LocalDate retentionUntil) {
        requireStatus(ProjectStatus.COMPLETION_PENDING);
        positions.forEach(Position::close);
        this.status = ProjectStatus.CLOSED;
        this.paymentStatus = ProjectPaymentStatus.SUCCESS_FEE_PAID;
        this.closedAt = LocalDateTime.now();
        this.retentionUntil = retentionUntil;
    }

    // ==========================================
    // 조회
    // ==========================================

    public boolean isHeadcountChangeable() {
        return status == ProjectStatus.REGISTERED;
    }

    public boolean isOwnedBy(Long clientProfileId) {
        return Objects.equals(this.clientId, clientProfileId);
    }

    // ==========================================
    // 검증
    // ==========================================

    private static int sumHeadcount(List<Position> positions) {
        return positions.stream().mapToInt(Position::getHeadcount).sum();
    }

    private void requireStatus(ProjectStatus required) {
        if (this.status != required) {
            throw new BusinessException(ProjectErrorCode.INVALID_STATUS);
        }
    }

    private static void validate(Long clientId, String title, LocalDate startDesiredDate,
                                 int periodValue, PeriodUnit periodUnit, Long budgetAmount,
                                 WorkStyle workStyle, WorkForm workForm,
                                 String currentSituation, String mainTask,
                                 String detailScope, String extraNote,
                                 List<Position> positions, List<Long> fileIds) {
        if (clientId == null) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (positions.isEmpty() || positions.size() > MAX_POSITIONS) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        validateBasic(title, startDesiredDate, periodValue, periodUnit, budgetAmount,
                workStyle, workForm, currentSituation, mainTask, detailScope, extraNote, fileIds);
    }

    private static void validateBasic(String title, LocalDate startDesiredDate,
                                      int periodValue, PeriodUnit periodUnit, Long budgetAmount,
                                      WorkStyle workStyle, WorkForm workForm,
                                      String currentSituation, String mainTask,
                                      String detailScope, String extraNote, List<Long> fileIds) {
        if (isBlank(title) || title.length() > MAX_TITLE) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (startDesiredDate == null || periodUnit == null || workStyle == null || workForm == null) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (periodValue < 1 || periodValue > MAX_PERIOD) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (budgetAmount == null || budgetAmount < MIN_BUDGET || budgetAmount > MAX_BUDGET) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (isBlank(currentSituation) || isBlank(mainTask)) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (overLength(currentSituation) || overLength(mainTask)
                || overLength(detailScope) || overLength(extraNote)) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
        if (fileIds.size() > MAX_FILES) {
            throw new BusinessException(ProjectErrorCode.INVALID_PROJECT_FIELD);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean overLength(String s) {
        return s != null && s.length() > MAX_TEXT;
    }
}