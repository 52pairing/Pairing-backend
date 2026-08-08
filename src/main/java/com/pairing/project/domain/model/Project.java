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
            result.add(existing);
            no++;
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

    /** 1주 단위, 최대 2회. 상한을 넘겨 중단하면 클라이언트 파기로 본다. */
    public void extendRecruit() {
        requireStatus(ProjectStatus.RECRUITING);
        if (extensionCount >= MAX_EXTENSION) {
            throw new BusinessException(ProjectErrorCode.EXTENSION_LIMIT_EXCEEDED);
        }
        this.extensionCount++;
        this.recruitDeadline = this.recruitDeadline.plusWeeks(EXTENSION_WEEKS);
    }

    /** 남은 기간과 무관하게 모집을 닫는다. 진행 중인 협상·계약은 그대로 이어진다. */
    public void closeRecruit() {
        requireStatus(ProjectStatus.RECRUITING);
        positions.stream().filter(p -> !p.isFilled()).forEach(Position::close);
        this.recruitDeadline = LocalDateTime.now();
    }

    public void cancel(LocalDate retentionUntil) {
        if (status == ProjectStatus.CANCELED || status == ProjectStatus.CLOSED) {
            throw new BusinessException(ProjectErrorCode.INVALID_STATUS);
        }
        this.status = ProjectStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.retentionUntil = retentionUntil;
    }

    public void close(LocalDate retentionUntil) {
        this.status = ProjectStatus.CLOSED;
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