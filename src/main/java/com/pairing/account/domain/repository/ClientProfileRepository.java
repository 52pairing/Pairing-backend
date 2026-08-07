package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.ClientProfile;

import java.util.Optional;

public interface ClientProfileRepository {

    ClientProfile save(ClientProfile clientProfile);

    Optional<ClientProfile> findById(Long id);

    Optional<ClientProfile> findByAccountId(Long accountId);

    boolean existsByBusinessNo(String businessNo);
}
