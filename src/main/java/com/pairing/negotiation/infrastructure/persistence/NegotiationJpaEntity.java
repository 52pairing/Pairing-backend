package com.pairing.negotiation.infrastructure.persistence;

import com.pairing.negotiation.domain.model.NegotiationAgentState;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "negotiation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NegotiationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NegotiationStatus status;

    @Column(name = "total_round", nullable = false)
    private int totalRound;

    @Column(name = "agreed_amount")
    private Long agreedAmount;

    @Column(name = "budget_cap", nullable = false)
    private Long budgetCap;

    @Column(name = "freelancer_monthly_pay")
    private Long freelancerMonthlyPay;

    @Column(name = "floor_amount", nullable = false)
    private Long floorAmount;

    @Column(name = "ai_out_at")
    private LocalDateTime aiOutAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "client_last_read_at")
    private LocalDateTime clientLastReadAt;

    @Column(name = "freelancer_last_read_at")
    private LocalDateTime freelancerLastReadAt;

    // 대리인(A2A) 실행 상태. A2A 호출이 요청 스레드 밖에서 돌기 때문에 저장이 필요하다.
    //
    // nullable 이어야 한다. 배포된 RDS 에 ddl-auto: update 로 컬럼이 새로 붙는 것이라
    // 이미 있던 협상 행에는 값이 안 들어간다. null 은 도메인이 IDLE 로 읽는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_state", length = 20)
    private NegotiationAgentState agentState;

    @Column(name = "agent_started_at")
    private LocalDateTime agentStartedAt;

    // 최종 절충 단계 상태. agent_state 와 같은 이유로 nullable(Boolean) 이다 — ddl-auto: update 로
    // 배포된 RDS 에 컬럼이 새로 붙는데, NOT NULL 컬럼을 채워진 테이블에 추가하면 실패한다. 도메인은
    // null 을 false 로 읽는다(매퍼에서 처리).
    @Column(name = "final_offer")
    private Boolean finalOffer;

    @Column(name = "client_final_accepted")
    private Boolean clientFinalAccepted;

    @Column(name = "freelancer_final_accepted")
    private Boolean freelancerFinalAccepted;

    // 애그리거트: 조건은 협상과 생명주기를 함께한다(cascade + orphanRemoval).
    //
    // @OrderBy 가 없으면 DB 가 돌려주는 순서를 그대로 쓴다 — 즉 매번 달라질 수 있다.
    // 그래서 sort_order 컬럼에 1..5 가 멀쩡히 들어 있는데도 아무도 안 보고 있었고,
    // 실제로 두 군데에서 티가 났다(2026-08-12 실측).
    //   1) 화면 — 조건 배지 순서가 새로고침할 때마다 바뀐다
    //   2) AI — A2A 프롬프트의 쟁점 나열 순서가 호출마다 달라진다. LLM 은 앞에 온 항목에
    //      더 영향을 받으므로, 같은 입력에 결과가 흔들리는 원인이 된다(재현성)
    // id 를 2차 정렬로 두는 건 sort_order 가 같은 옛 데이터에서도 순서가 고정되게 하려는 것이다.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "negotiation_id")
    @OrderBy("sortOrder ASC, id ASC")
    private List<NegotiationConditionJpaEntity> conditions = new ArrayList<>();

    public NegotiationJpaEntity(Long id, Long requestId, Long projectId, Long positionId, Long freelancerId,
                                NegotiationStatus status, int totalRound, Long agreedAmount, Long budgetCap,
                                Long freelancerMonthlyPay, Long floorAmount,
                                LocalDateTime aiOutAt, LocalDateTime startedAt,
                                LocalDateTime endedAt, String endReason, LocalDateTime clientLastReadAt,
                                LocalDateTime freelancerLastReadAt,
                                NegotiationAgentState agentState, LocalDateTime agentStartedAt,
                                Boolean finalOffer, Boolean clientFinalAccepted, Boolean freelancerFinalAccepted,
                                List<NegotiationConditionJpaEntity> conditions) {
        this.id = id;
        this.requestId = requestId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.freelancerId = freelancerId;
        this.status = status;
        this.totalRound = totalRound;
        this.agreedAmount = agreedAmount;
        this.budgetCap = budgetCap;
        this.freelancerMonthlyPay = freelancerMonthlyPay;
        this.floorAmount = floorAmount;
        this.aiOutAt = aiOutAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.endReason = endReason;
        this.clientLastReadAt = clientLastReadAt;
        this.freelancerLastReadAt = freelancerLastReadAt;
        this.agentState = agentState;
        this.agentStartedAt = agentStartedAt;
        this.finalOffer = finalOffer;
        this.clientFinalAccepted = clientFinalAccepted;
        this.freelancerFinalAccepted = freelancerFinalAccepted;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
    }
}
