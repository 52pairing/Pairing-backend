package com.pairing.account.presentation.advice;

import com.pairing.global.exception.CommonExceptionAdvice;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** account 도메인 컨트롤러의 예외만 처리한다. */
@Slf4j
@RestControllerAdvice(basePackages = "com.pairing.account.presentation.api")
public class AccountExceptionAdvice implements CommonExceptionAdvice {

    @Override
    public Logger getLogger() {
        return log;
    }
}
