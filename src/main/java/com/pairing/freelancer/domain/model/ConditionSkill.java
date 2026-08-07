package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import lombok.Getter;

/** 보유 스킬 한 건(스킬 + 숙련도). 조건 저장 시 전체 교체된다. */
@Getter
public class ConditionSkill {

    private final SkillCode skillCode;
    private final SkillLevel skillLevel;

    private ConditionSkill(SkillCode skillCode, SkillLevel skillLevel) {
        if (skillCode == null || skillLevel == null) {
            throw new BusinessException(FreelancerErrorCode.INVALID_CONDITION_FIELD);
        }
        this.skillCode = skillCode;
        this.skillLevel = skillLevel;
    }

    public static ConditionSkill of(SkillCode skillCode, SkillLevel skillLevel) {
        return new ConditionSkill(skillCode, skillLevel);
    }
}
