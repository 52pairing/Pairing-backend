package com.pairing.negotiation.infrastructure.project;

import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

/**
 * project 테이블 읽기 전용 매핑(협상 소유). 협상이 role 판정·표시·생성 diff 에 필요한 컬럼만 읽는다.
 * project 도메인이 소유권을 가지므로 여기서는 절대 쓰지 않는다({@link Immutable}).
 */
@Entity
@Immutable
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectReadJpaEntity {

    @Id
    private Long id;

    /** client_profile.id (account.id 아님). */
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "title")
    private String title;

    @Column(name = "budget_amount")
    private Long budgetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style")
    private WorkStyle workStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_form")
    private WorkForm workForm;

    @Column(name = "start_desired_date")
    private LocalDate startDesiredDate;

    @Column(name = "start_negotiable")
    private boolean startNegotiable;
}
