package com.pairing.terms.application.service;

import com.pairing.terms.application.usecase.TermsQueryUseCase;
import com.pairing.terms.domain.model.Terms;
import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.domain.repository.TermsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TermsQueryService implements TermsQueryUseCase {

    private final TermsRepository termsRepository;

    @Override
    public List<Terms> findLatestByRole(String targetRole) {
        // 조회 결과는 code 오름차순 + 시행일 내림차순이라, code별 첫 항목이 최신 버전이다.
        Map<TermsCode, Terms> latestByCode = new EnumMap<>(TermsCode.class);

        for (Terms terms : termsRepository.findEffectiveByRole(targetRole, LocalDateTime.now())) {
            latestByCode.putIfAbsent(terms.getCode(), terms);
        }

        return new ArrayList<>(latestByCode.values());
    }
}
