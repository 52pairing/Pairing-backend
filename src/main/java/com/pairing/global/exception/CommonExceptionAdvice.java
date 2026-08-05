package com.pairing.global.exception;

import com.pairing.global.common.api.response.ErrorResponse;
import com.pairing.global.filter.TraceIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 예외 처리 공통 규약.
 * 도메인별 @RestControllerAdvice 클래스가 이 인터페이스를 구현하면
 * BusinessException / Validation / 400 / 404 / 500 처리를 그대로 물려받는다.
 */
public interface CommonExceptionAdvice {

    Logger getLogger();

    /** 메트릭 기록이 필요한 구현체만 오버라이드 (기본값 null → 메트릭 미기록) */
    default MeterRegistry getMeterRegistry() {
        return null;
    }

    /** api_errors_total{reason=...} 카운터 증가 */
    default void recordApiError(String reason) {
        MeterRegistry registry = getMeterRegistry();
        if (registry != null) {
            registry.counter("api_errors_total", "reason", reason).increment();
        }
    }

    // 0. 인가(Authorization) 실패 — @PreAuthorize 거부
    //
    //    permitAll 경로 안의 @PreAuthorize 메서드는 비로그인 요청도 컨트롤러까지 도달한다.
    //    이때 403을 주면 "로그인은 됐는데 권한이 없다"로 읽혀 프론트가 재로그인 유도를 못 한다.
    //    로그인 자체가 안 된 경우는 401로 구분한다.
    @ExceptionHandler(AccessDeniedException.class)
    default ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = isAnonymous()
                ? GlobalErrorCode.UNAUTHORIZED
                : GlobalErrorCode.ACCESS_DENIED;

        getLogger().warn("[AccessDeniedException] traceId: {}, code: {}, message: {}",
                traceId, errorCode.getCode(), e.getMessage());
        recordApiError(errorCode == GlobalErrorCode.UNAUTHORIZED ? "unauthorized" : "access_denied");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    private static boolean isAnonymous() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    // 1. 비즈니스 로직 에러
    @ExceptionHandler(BusinessException.class)
    default ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        String traceId = getOrCreateTraceId();
        BaseErrorCode errorCode = e.getErrorCode();

        getLogger().warn("[BusinessException] traceId: {}, code: {}, message: {}",
                traceId, errorCode.getCode(), errorCode.getMessage());
        recordApiError("business");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    // 2. @Valid 어노테이션 유효성 검사 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    default ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.INVALID_REQUEST;

        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        getLogger().warn("[ValidationException] traceId: {}, message: {}", traceId, errorMessage);
        recordApiError("validation");

        return toResponse(errorCode, errorMessage.isEmpty() ? errorCode.getMessage() : errorMessage, traceId);
    }

    // 2-1. @Validated 컨트롤러의 쿼리 파라미터/경로 변수 검증 실패
    //      이걸 처리하지 않으면 아래 Exception 핸들러로 떨어져 400이어야 할 응답이 500으로 나간다.
    @ExceptionHandler(ConstraintViolationException.class)
    default ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.INVALID_REQUEST;

        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));

        getLogger().warn("[ConstraintViolationException] traceId: {}, message: {}", traceId, errorMessage);
        recordApiError("validation");

        return toResponse(errorCode, errorMessage.isEmpty() ? errorCode.getMessage() : errorMessage, traceId);
    }

    // 3. API는 존재하지만 파라미터 타입이 안 맞거나, 값이 누락되거나, JSON 구조가 잘못된 경우
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    default ResponseEntity<ErrorResponse> handleBadRequestExceptions(Exception e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.INVALID_REQUEST;
        String errorMessage = errorCode.getMessage();

        if (e instanceof MethodArgumentTypeMismatchException mismatchException) {
            errorMessage = String.format("파라미터 '%s'의 타입이 올바르지 않습니다. (요청 값: %s)",
                    mismatchException.getName(), mismatchException.getValue());
        } else if (e instanceof MissingServletRequestParameterException missingException) {
            errorMessage = String.format("필수 쿼리 파라미터 '%s'가 누락되었습니다.", missingException.getParameterName());
        } else if (e instanceof HttpMessageNotReadableException) {
            errorMessage = "요청 본문(Body)의 JSON 형식이 올바르지 않거나 데이터 타입이 일치하지 않습니다.";
        }

        getLogger().warn("[BadRequestException] traceId: {}, message: {}", traceId, errorMessage);
        recordApiError("bad_request");

        return toResponse(errorCode, errorMessage, traceId);
    }

    // 4. API 경로는 일치하지만 HTTP 메서드(GET, POST 등)가 잘못된 경우
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    default ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.METHOD_NOT_ALLOWED;

        getLogger().warn("[HttpRequestMethodNotSupportedException] traceId: {}, message: {}", traceId, e.getMessage());
        recordApiError("method_not_allowed");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    // 5. 요청한 API 경로가 아예 존재하지 않는 경우 (404)
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    default ResponseEntity<ErrorResponse> handleNotFoundException(Exception e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.API_NOT_FOUND;

        getLogger().warn("[NotFoundException] traceId: {}, message: {}", traceId, e.getMessage());
        recordApiError("not_found");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    // 6. 그 외 예상치 못한 서버 에러 최후의 보루
    @ExceptionHandler(Exception.class)
    default ResponseEntity<ErrorResponse> handleException(Exception e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode = GlobalErrorCode.SERVER_ERROR;

        getLogger().error("[InternalServerError] traceId: {} - {}", traceId, errorCode.getMessage(), e);
        recordApiError("server_error");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    private ResponseEntity<ErrorResponse> toResponse(BaseErrorCode errorCode, String message, String traceId) {
        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                traceId
        );
        return ResponseEntity.status(errorCode.getStatus()).body(response);
    }

    /** 시큐리티 필터 쪽(ErrorResponseWriter)과 같은 traceId를 쓰도록 조회 로직을 한 곳에 모았다. */
    default String getOrCreateTraceId() {
        return TraceIdFilter.currentTraceId();
    }
}
