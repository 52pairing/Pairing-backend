package com.pairing.global.exception;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice // 전역 에러 처리 (도메인별 Advice가 잡지 못한 예외를 최종 처리)
public class GlobalExceptionHandler implements CommonExceptionAdvice {

    private final MeterRegistry meterRegistry;

    @Override
    public Logger getLogger() {
        return log;
    }

    @Override
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
