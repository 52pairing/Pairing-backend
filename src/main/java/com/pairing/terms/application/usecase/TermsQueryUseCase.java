package com.pairing.terms.application.usecase;

import com.pairing.terms.domain.model.Terms;

import java.util.List;

public interface TermsQueryUseCase {

    /** 가입 화면에 노출할 동의 항목. 게시용 문서(개인정보 처리방침)는 빠진다. */
    List<Terms> findLatestByRole(String targetRole);

    /** 푸터 등에서 전문을 열람하는 문서 목록. 동의 항목과 게시용 문서를 모두 포함한다. */
    List<Terms> findLatestDocuments(String targetRole);
}
