package com.pairing.project.infrastructure.persistence;

import com.pairing.meta.domain.model.SkillCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * position_skill 테이블 매핑. 포지션의 요구 스킬 1건이다.
 *
 * <p>created_at / updated_at 은 DB 기본값이 채운다.
 */
@Entity
@Table(name = "position_skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionSkillJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id", nullable = false)
    private ProjectPositionJpaEntity position;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_code", nullable = false, length = 50)
    private SkillCode skillCode;

    public PositionSkillJpaEntity(Long id, SkillCode skillCode) {
        this.id = id;
        this.skillCode = skillCode;
    }

    void assignPosition(ProjectPositionJpaEntity position) {
        this.position = position;
    }
}