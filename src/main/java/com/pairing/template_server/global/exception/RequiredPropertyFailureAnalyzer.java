package com.pairing.template_server.global.exception;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * {@link RequiredPropertyMissingException}을 사람이 읽을 수 있는 기동 실패 메시지로 바꿔준다.
 *
 * <p>이 클래스가 없으면 설정값 누락이 "Error creating bean with name ... Unsatisfied dependency" 같은
 * 원인 불명 메시지로만 보이고, 정작 필요한 안내는 스택트레이스 수십 줄 아래에 묻힌다.
 *
 * <p>등록 위치: {@code src/main/resources/META-INF/spring.factories}
 */
public class RequiredPropertyFailureAnalyzer extends AbstractFailureAnalyzer<RequiredPropertyMissingException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, RequiredPropertyMissingException cause) {
        String description = """
                필수 설정값이 없어 애플리케이션을 시작할 수 없습니다.

                  설정 키   : %s
                  환경변수  : %s
                  상세      : %s""".formatted(
                cause.getPropertyName(),
                cause.getEnvironmentVariable(),
                cause.getMessage());

        return new FailureAnalysis(description, cause.getAction(), cause);
    }
}
