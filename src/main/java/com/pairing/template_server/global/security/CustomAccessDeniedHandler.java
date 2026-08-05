package com.pairing.template_server.global.security;

import com.pairing.template_server.global.exception.ErrorResponseWriter;
import com.pairing.template_server.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증은 됐지만 권한이 없는 요청(403) 응답을 만든다.
 * {@code @PreAuthorize} 거부 시 CommonExceptionAdvice가 내려주는 응답과 형식이 같다.
 */
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        errorResponseWriter.write(response, GlobalErrorCode.ACCESS_DENIED);
    }
}
