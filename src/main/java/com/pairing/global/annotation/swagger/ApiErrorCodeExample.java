package com.pairing.global.annotation.swagger;

import com.pairing.global.exception.BaseErrorCode;

import java.lang.annotation.*;

/**
 * 컨트롤러 메서드에 붙이면 해당 에러코드의 예시 응답이 Swagger 문서에 자동으로 추가된다.
 * (실제 문서 조립은 SwaggerConfig의 OperationCustomizer가 담당)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodeExamples.class) // 여러 개를 중복해서 달 수 있도록 설정
public @interface ApiErrorCodeExample {
    Class<? extends BaseErrorCode> domain(); // 도메인 Enum 클래스 (예: ExampleErrorCode.class)
    String[] value();                        // 발생할 에러 코드 이름들 (예: {"EXAMPLE_NOT_FOUND"})
}
