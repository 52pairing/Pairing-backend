package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.freelancer.domain.model.FreelancerCondition;
import com.pairing.freelancer.domain.repository.FreelancerConditionRepository;
import com.pairing.freelancer.infrastructure.mapper.FreelancerConditionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FreelancerConditionRepositoryAdapter implements FreelancerConditionRepository {

    private final SpringDataFreelancerConditionRepository springDataRepository;
    private final FreelancerConditionMapper conditionMapper;

    @Override
    public FreelancerCondition save(FreelancerCondition condition) {
        FreelancerConditionJpaEntity saved = springDataRepository.save(conditionMapper.toJpaEntity(condition));
        return conditionMapper.toDomain(saved);
    }

    @Override
    public Optional<FreelancerCondition> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountId(accountId).map(conditionMapper::toDomain);
    }
}
