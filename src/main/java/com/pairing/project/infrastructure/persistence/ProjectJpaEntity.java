package com.pairing.project.infrastructure.persistence;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * project 테이블 매핑.
 *
 * <p>created_at / updated_at 은 쓰기 매핑하지 않는다. DB 기본값과 트리거가 채운다.
 * 다만 응답에 등록 시각이 필요해 createdAt 만 읽기 전용으로 둔다.
 *
 * <p>client_id 는 client_profile.id 다. account.id 가 아니다.
 *
 * <p>positions / files 는 생성자에서 제외한다. 양방향 연관이라 addPosition / addFile 로만
 * 걸어야 양쪽 참조가 어긋나지 않는다.
 */
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "start_desired_date")
    private LocalDate startDesiredDate;

    @Column(name = "start_negotiable", nullable = false)
    private boolean startNegotiable;

    @Column(name = "period_value", nullable = false)
    private int periodValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_unit", nullable = false, length = 10)
    private PeriodUnit periodUnit;

    @Column(name = "budget_amount", nullable = false, precision = 15, scale = 0)
    private Long budgetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style", nullable = false, length = 20)
    private WorkStyle workStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_form", nullable = false, length = 20)
    private WorkForm workForm;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @Column(name = "current_situation", length = 1500)
    private String currentSituation;

    @Column(name = "main_task", length = 1500)
    private String mainTask;

    @Column(name = "detail_scope", length = 1500)
    private String detailScope;

    @Column(name = "extra_note", length = 1500)
    private String extraNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private ProjectPaymentStatus paymentStatus;

    @Column(name = "total_headcount", nullable = false)
    private int totalHeadcount;

    @Column(name = "confirmed_headcount", nullable = false)
    private int confirmedHeadcount;

    @Column(name = "recruit_started_at")
    private LocalDateTime recruitStartedAt;

    @Column(name = "recruit_deadline")
    private LocalDateTime recruitDeadline;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "free_rerecommend_used", nullable = false)
    private int freeRerecommendUsed;

    @Column(name = "paid_rerecommend_used", nullable = false)
    private int paidRerecommendUsed;

    @Column(name = "notice_agreed_at")
    private LocalDateTime noticeAgreedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("positionNo ASC")
    @BatchSize(size = 100)
    private List<ProjectPositionJpaEntity> positions = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 100)
    private List<ProjectFileJpaEntity> files = new ArrayList<>();

    public ProjectJpaEntity(Long id, Long clientId, String title, LocalDate startDesiredDate,
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
                            LocalDateTime createdAt, LocalDateTime deletedAt) {
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
        this.deletedAt = deletedAt;
    }

    /**
     * 상태 전이 결과만 반영한다. positions / files 는 손대지 않는다.
     *
     * <p>매퍼로 새 그래프를 만들어 save 하면 자식이 다시 INSERT 되어
     * {@code uk_position_skill} 에 걸린다. 상태만 바뀌는 경로는 영속 엔티티를 로드해 이걸 쓴다.
     */
    public void applyState(ProjectStatus status, ProjectPaymentStatus paymentStatus,
                           int totalHeadcount, int confirmedHeadcount,
                           LocalDateTime recruitStartedAt, LocalDateTime recruitDeadline,
                           int extensionCount, int freeRerecommendUsed, int paidRerecommendUsed,
                           LocalDateTime canceledAt, LocalDateTime closedAt,
                           LocalDate retentionUntil) {
        this.status = status;
        this.paymentStatus = paymentStatus;
        this.totalHeadcount = totalHeadcount;
        this.confirmedHeadcount = confirmedHeadcount;
        this.recruitStartedAt = recruitStartedAt;
        this.recruitDeadline = recruitDeadline;
        this.extensionCount = extensionCount;
        this.freeRerecommendUsed = freeRerecommendUsed;
        this.paidRerecommendUsed = paidRerecommendUsed;
        this.canceledAt = canceledAt;
        this.closedAt = closedAt;
        this.retentionUntil = retentionUntil;
    }

    /** 수정으로 바뀌는 스칼라. 상태·확정 인원·재추천 횟수는 여기서 다루지 않는다. */
    public void applyEditable(String title, LocalDate startDesiredDate, boolean startNegotiable,
                              int periodValue, PeriodUnit periodUnit, Long budgetAmount,
                              WorkStyle workStyle, WorkForm workForm, String workLocation,
                              String currentSituation, String mainTask, String detailScope,
                              String extraNote, int totalHeadcount) {
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
        this.totalHeadcount = totalHeadcount;
    }

    public void addPosition(ProjectPositionJpaEntity position) {
        positions.add(position);
        position.assignProject(this);
    }

    public void addFile(ProjectFileJpaEntity file) {
        files.add(file);
        file.assignProject(this);
    }

    /**
     * 요청에 없는 포지션을 지운다. {@code orphanRemoval} 이 DELETE 를 만든다.
     *
     * <p>도메인이 REGISTERED 상태에서만 삭제를 허용하므로, 여기까지 온 포지션은
     * 매칭·계약이 참조하지 않는 것들이다.
     */
    public void removePositionsNotIn(Set<Long> keepIds) {
        positions.removeIf(position -> position.getId() != null && !keepIds.contains(position.getId()));
    }

    /** 첨부는 참조하는 테이블이 없어 전량 교체한다. */
    public void clearFiles() {
        this.files.clear();
    }
}