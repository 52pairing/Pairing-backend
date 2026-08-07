package com.pairing.negotiation.infrastructure.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * project 테이블 읽기 전용 매핑(협상 소유). 협상이 role 판정·표시에 필요한 컬럼만 읽는다.
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
}
