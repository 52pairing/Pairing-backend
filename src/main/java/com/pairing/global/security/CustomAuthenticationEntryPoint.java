package com.pairing.global.security;

import com.pairing.global.exception.ErrorResponseWriter;
import com.pairing.global.exception.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증되지 않은 요청(401) 응답을 만든다.
 * 응답 형식은 {@code @RestControllerAdvice}가 내려주는 ErrorResponse와 동일하다.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        errorResponseWriter.write(response, GlobalErrorCode.UNAUTHORIZED);
    }
}
