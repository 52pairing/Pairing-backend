package com.pairing.terms.domain.repository;

import com.pairing.terms.domain.model.Terms;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface TermsRepository {

    /** 시행일이 지난 약관 중 해당 역할에 해당하는 것을 최신 시행일 순으로 반환한다. */
    List<Terms> findEffectiveByRole(String targetRole, LocalDateTime now);

    List<Terms> findAllByIds(Collection<Long> ids);
}
