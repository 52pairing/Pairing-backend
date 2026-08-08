package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.infrastructure.mapper.ClientProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ClientProfileRepositoryAdapter implements ClientProfileRepository {

    private final SpringDataClientProfileRepository springDataRepository;
    private final ClientProfileMapper clientProfileMapper;

    @Override
    public ClientProfile save(ClientProfile clientProfile) {
        ClientProfileJpaEntity saved = springDataRepository.save(clientProfileMapper.toJpaEntity(clientProfile));
        return clientProfileMapper.toDomain(saved);
    }

    @Override
    public Optional<ClientProfile> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountIdAndDeletedAtIsNull(accountId).map(clientProfileMapper::toDomain);
    }

    @Override
    public Optional<ClientProfile> findById(Long id) {
        return springDataRepository.findByIdAndDeletedAtIsNull(id).map(clientProfileMapper::toDomain);
    }

    @Override
    public boolean existsByBusinessNo(String businessNo) {
        return springDataRepository.existsByBusinessNo(businessNo);
    }
}
