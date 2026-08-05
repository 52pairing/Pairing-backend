package com.pairing.terms.application.usecase;

import com.pairing.terms.application.command.AgreeTermsCommand;

import java.util.List;

public interface TermsAgreementCommandUseCase {

    /**
     * 약관 동의를 기록한다. 역할별 필수 약관이 하나라도 빠지거나 false면 예외를 던진다.
     *
     * @param targetRole CLIENT / FREELANCER
     */
    void agreeAll(Long accountId, String targetRole, List<AgreeTermsCommand> agreements, String userAgent);
}
