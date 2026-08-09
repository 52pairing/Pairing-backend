package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.account.infrastructure.mapper.FreelancerProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FreelancerProfileRepositoryAdapter implements FreelancerProfileRepository {

    private final SpringDataFreelancerProfileRepository springDataRepository;
    private final FreelancerProfileMapper freelancerProfileMapper;

    @Override
    public FreelancerProfile save(FreelancerProfile freelancerProfile) {
        FreelancerProfileJpaEntity saved =
                springDataRepository.save(freelancerProfileMapper.toJpaEntity(freelancerProfile));
        return freelancerProfileMapper.toDomain(saved);
    }

    @Override
    public Optional<FreelancerProfile> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountIdAndDeletedAtIsNull(accountId)
                .map(freelancerProfileMapper::toDomain);
    }

    @Override
    public Optional<FreelancerProfile> findById(Long id) {
        return springDataRepository.findByIdAndDeletedAtIsNull(id).map(freelancerProfileMapper::toDomain);
    }

    @Override
    public List<Long> filterActiveAiMatchingAgreed(Collection<Long> accountIds) {
        return springDataRepository.filterActiveAiMatchingAgreed(accountIds);
    }
}
