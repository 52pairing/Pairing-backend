package com.pairing.freelancer.infrastructure.mapper;

import com.pairing.freelancer.domain.model.ConditionSkill;
import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.freelancer.infrastructure.persistence.ConditionSkillEmbeddable;
import com.pairing.freelancer.infrastructure.persistence.FreelancerConditionJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FreelancerConditionMapper {

    FreelancerConditionJpaEntity toJpaEntity(FreelancerCondition condition);

    ConditionSkillEmbeddable toEmbeddable(ConditionSkill skill);

    // ConditionSkill 생성자가 닫혀 있어 MapStruct 가 직접 만들 수 없다. of(...) 로 위임한다.
    default ConditionSkill toDomainSkill(ConditionSkillEmbeddable embeddable) {
        if (embeddable == null) {
            return null;
        }
        return ConditionSkill.of(embeddable.getSkillCode(), embeddable.getSkillLevel());
    }

    default FreelancerCondition toDomain(FreelancerConditionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<ConditionSkill> skills = entity.getSkills().stream().map(this::toDomainSkill).toList();
        return FreelancerCondition.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getJobCategory(),
                entity.getJobRole(),
                entity.getWorkStyle(),
                entity.getWorkForm(),
                entity.getPayUnit(),
                entity.getPayAmount(),
                entity.getMinAcceptAmount(),
                entity.getAvailableFrom(),
                entity.isStartNegotiable(),
                entity.getPeriodValue(),
                entity.getPeriodUnit(),
                entity.isHasFreelanceExperience(),
                entity.getCareerYears(),
                skills
        );
    }
}
