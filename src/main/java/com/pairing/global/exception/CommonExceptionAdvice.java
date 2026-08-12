package com.pairing.global.exception;

import com.pairing.global.common.api.response.ErrorResponse;
import com.pairing.global.filter.TraceIdFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

import java.sql.SQLException;
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

        // 예외가 들고 온 문구를 우선한다. 기본 생성자는 errorCode 문구를 그대로 담으므로 동작이 같고,
        // 상황별 문구(예: IP 차단 해제 시각)를 넘긴 경우에만 달라진다.
        String message = e.getMessage() != null ? e.getMessage() : errorCode.getMessage();

        getLogger().warn("[BusinessException] traceId: {}, code: {}, message: {}",
                traceId, errorCode.getCode(), message);
        recordApiError("business");

        return toResponse(errorCode, message, traceId);
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

    // 5-1. 멀티파트 요청이 규격에 안 맞는 경우
    //
    //      셋 다 아래 Exception 핸들러로 떨어져 500 GLOBAL_001 로 나가던 것들이다.
    //      파일 업로드는 프론트가 part 이름·Content-Type 을 틀리기 쉬운데, 500 이 오면
    //      서버 장애로 읽혀 원인을 엉뚱한 데서 찾게 된다.
    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MaxUploadSizeExceededException.class,
            HttpMediaTypeNotSupportedException.class
    })
    default ResponseEntity<ErrorResponse> handleMultipartExceptions(Exception e) {
        String traceId = getOrCreateTraceId();
        GlobalErrorCode errorCode;
        String errorMessage;

        if (e instanceof MissingServletRequestPartException missingPart) {
            errorCode = GlobalErrorCode.INVALID_REQUEST;
            errorMessage = String.format("필수 파일 파트 '%s'가 누락되었습니다.", missingPart.getRequestPartName());
        } else if (e instanceof MaxUploadSizeExceededException) {
            errorCode = GlobalErrorCode.PAYLOAD_TOO_LARGE;
            errorMessage = errorCode.getMessage();
        } else {
            errorCode = GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE;
            errorMessage = "파일 업로드는 multipart/form-data 로 보내야 합니다.";
        }

        getLogger().warn("[MultipartException] traceId: {}, code: {}, message: {}",
                traceId, errorCode.getCode(), errorMessage);
        recordApiError("bad_request");

        return toResponse(errorCode, errorMessage, traceId);
    }

    // 5-2. DB 제약 위반
    //
    //      이걸 안 잡으면 UNIQUE 중복부터 스키마가 코드와 어긋난 CHECK 위반까지 전부
    //      GLOBAL_001 로 뭉개져, 응답만 보고는 사용자 잘못인지 서버 잘못인지 구분할 수 없다.
    //      SQLState 로 갈라 원인을 구분하고, 어느 제약이 터졌는지 로그에 남긴다.
    @ExceptionHandler(DataIntegrityViolationException.class)
    default ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        String traceId = getOrCreateTraceId();
        String sqlState = findSqlState(e);

        // 23505 중복 / 23503 참조 무결성 -> 요청을 바꾸면 풀리므로 409.
        // 23514 CHECK / 23502 NOT NULL -> 사용자가 손댈 수 없는 서버·스키마 문제라 500.
        boolean conflict = "23505".equals(sqlState) || "23503".equals(sqlState);
        GlobalErrorCode errorCode = conflict
                ? GlobalErrorCode.DATA_CONFLICT
                : GlobalErrorCode.DATA_INTEGRITY_ERROR;

        if (conflict) {
            getLogger().warn("[DataIntegrityViolation] traceId: {}, sqlState: {}, message: {}",
                    traceId, sqlState, e.getMostSpecificCause().getMessage());
        } else {
            // 스키마 불일치는 배포로만 고칠 수 있다. 원인 메시지(제약 이름 포함)를 통째로 남긴다.
            getLogger().error("[DataIntegrityViolation] traceId: {}, sqlState: {} - 스키마 제약 위반",
                    traceId, sqlState, e);
        }
        recordApiError(conflict ? "data_conflict" : "data_integrity");

        return toResponse(errorCode, errorCode.getMessage(), traceId);
    }

    /** 원인 사슬을 따라 내려가 JDBC 표준 SQLState 를 찾는다. 없으면 null. */
    private static String findSqlState(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
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
