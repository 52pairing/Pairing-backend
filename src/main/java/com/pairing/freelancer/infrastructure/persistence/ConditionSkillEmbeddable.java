package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** condition_skill 테이블의 한 행. freelancer_condition 에 종속된다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConditionSkillEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_code", nullable = false, length = 30)
    private SkillCode skillCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false, length = 20)
    private SkillLevel skillLevel;

    public ConditionSkillEmbeddable(SkillCode skillCode, SkillLevel skillLevel) {
        this.skillCode = skillCode;
        this.skillLevel = skillLevel;
    }
}
