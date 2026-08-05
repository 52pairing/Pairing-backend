package com.pairing.terms.application.command;

/** 약관 하나에 대한 동의 여부. */
public record AgreeTermsCommand(
        Long termsId,
        boolean agreed
) {
}
