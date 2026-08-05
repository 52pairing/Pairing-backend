package com.pairing.terms.application.usecase;

import com.pairing.terms.domain.model.Terms;

import java.util.List;

public interface TermsQueryUseCase {

    /** 역할별로 지금 보여줘야 하는 약관(코드별 최신 버전) 목록. */
    List<Terms> findLatestByRole(String targetRole);
}
