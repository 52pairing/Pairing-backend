package com.pairing.terms.infrastructure.persistence;

import com.pairing.terms.domain.model.Terms;
import com.pairing.terms.domain.repository.TermsRepository;
import com.pairing.terms.infrastructure.mapper.TermsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TermsRepositoryAdapter implements TermsRepository {

    private final SpringDataTermsRepository springDataRepository;
    private final TermsMapper termsMapper;

    @Override
    public List<Terms> findEffectiveByRole(String targetRole, LocalDateTime now) {
        return springDataRepository.findEffectiveByRole(targetRole, now).stream()
                .map(termsMapper::toDomain)
                .toList();
    }

    @Override
    public List<Terms> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return springDataRepository.findAllByIdIn(ids).stream()
                .map(termsMapper::toDomain)
                .toList();
    }
}
