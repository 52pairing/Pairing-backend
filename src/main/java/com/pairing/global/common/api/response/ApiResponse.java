package com.pairing.global.common.api.response;

import java.time.Instant;

/*
 * ApiResponse는 성공 응답의 공통 표현 형식이다.
 * presentation 계층이 외부 계약을 표준화하고, 안쪽 계층은 HTTP 응답 구조를 몰라도 되게 만든다.
 * status는 HTTP 의미를, code와 message는 비즈니스 의미를 전달한다.
 */
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String code,
        String message,
        T data
) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(Instant.now(), 200, code, message, data);
    }

    // 생성과 일반 성공을 분리해두면 controller가 REST 의도를 더 명확히 표현할 수 있다.
    public static <T> ApiResponse<T> created(String code, String message, T data) {
        return new ApiResponse<>(Instant.now(), 201, code, message, data);
    }

    public static ApiResponse<Void> success(String code, String message) {
        return new ApiResponse<>(Instant.now(), 200, code, message, null);
    }

    /**
     * 요청은 접수했지만 처리는 아직 끝나지 않았을 때(비동기 처리). 완료를 기다리지 않고 바로
     * 응답하므로 결과 데이터가 없다 — 호출자는 "시작됐다"까지만 알 수 있다.
     */
    public static ApiResponse<Void> accepted(String code, String message) {
        return new ApiResponse<>(Instant.now(), 202, code, message, null);
    }
}
