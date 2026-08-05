package com.pairing.global.exception;

import com.pairing.global.common.api.response.ErrorResponse;
import com.pairing.global.filter.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * 시큐리티 필터체인처럼 {@code @RestControllerAdvice}가 닿지 않는 지점에서
 * 에러 응답을 만들 때 사용한다.
 *
 * <p>이게 없으면 필터·핸들러마다 손으로 JSON을 조립하게 되고, 같은 401/403인데도
 * 발생 위치에 따라 응답 형태가 달라진다. 형식을 {@link ErrorResponse} 하나로 통일하기 위한 창구다.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        write(response, errorCode, errorCode.getMessage());
    }

    public void write(HttpServletResponse response, BaseErrorCode errorCode, String message) throws IOException {
        // 이미 응답이 나가버린 뒤라면 덮어쓸 수 없다.
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                TraceIdFilter.currentTraceId()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
