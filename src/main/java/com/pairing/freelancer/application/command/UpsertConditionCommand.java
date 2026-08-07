package com.pairing.freelancer.application.command;

import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.util.List;

public record UpsertConditionCommand(
        Long accountId,
        JobCategory jobCategory,
        JobRole jobRole,
        String affiliation,
        WorkStyle workStyle,
        WorkForm workForm,
        PayUnit payUnit,
        Long payAmount,
        Long minAcceptAmount,
        LocalDate availableFrom,
        boolean startNegotiable,
        Integer periodValue,
        PeriodUnit periodUnit,
        boolean hasFreelanceExperience,
        int careerYears,
        List<Skill> skills
) {

    public record Skill(SkillCode skillCode, SkillLevel skillLevel) {
    }
}
