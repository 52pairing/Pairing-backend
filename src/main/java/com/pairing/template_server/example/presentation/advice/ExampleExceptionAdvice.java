package com.pairing.template_server.example.presentation.advice;

import com.pairing.template_server.global.exception.CommonExceptionAdvice;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
// Example 도메인 컨트롤러에서 발생하는 예외만 처리하도록 범위 지정
@RestControllerAdvice(basePackages = "com.example.template_server.example.presentation.api")
public class ExampleExceptionAdvice implements CommonExceptionAdvice {

    @Override
    public Logger getLogger() {
        return log; // ExampleExceptionAdvice 클래스의 로거를 인터페이스로 전달
    }

    // Example 도메인에서만 발생하는 아주 특수한 예외가 있다면 이곳에 추가로 @ExceptionHandler를 작성합니다.
    // (기본적인 BusinessException, Validation 등은 부모 인터페이스의 default 메서드가 모두 처리합니다.)
}