package com.pairing.notification.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.InternalCallGuard;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.presentation.api.request.InternalNotificationCreateRequest;
import com.pairing.notification.presentation.api.response.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 서버가 알림을 만들 때 쓰는 통로. 사용자가 직접 부르는 API 가 아니다.
 *
 * <p><b>왜 필요한가</b> — 관리자 서버가 {@code notification} 테이블에 직접 INSERT 하면 행은
 * 생기지만 <b>실시간 push 가 안 나간다.</b> WebSocket 세션은 이 서버(사용자 서버)가 들고 있어서,
 * 다른 프로세스는 그 세션에 아무것도 보낼 수 없다. 그래서 1:1 문의 답변 알림만 새로고침해야
 * 보이는 상태였다(2026-08-15 QA 에서 확인).
 *
 * <p>알림 생성을 이 서버 한 곳으로 모으면 저장과 push 가 항상 같이 일어난다. 부르는 쪽은
 * push 를 신경 쓸 필요가 없다.
 *
 * <p>같은 프로세스 안의 도메인(매칭·협상·계약)은 이 API 를 쓰지 않는다. 그쪽은
 * {@link NotificationCreateUseCase} 를 직접 호출하면 되고, HTTP 를 한 번 도는 건 낭비다.
 */
@RestController
@RequestMapping("/api/v1/internal/notifications")
@RequiredArgsConstructor
@Tag(name = "17. Notification", description = "알림 API")
public class InternalNotificationController {

    private final NotificationCreateUseCase notificationCreateUseCase;
    private final InternalCallGuard internalCallGuard;

    @PostMapping
    @Operation(summary = "[내부] 알림 생성",
            description = "다른 서버가 알림을 만들 때 호출합니다. 저장과 동시에 "
                    + "`/topic/users/{ownerAccountId}/notifications` 로 실시간 push 됩니다.\n\n"
                    + "**사용자 화면에서 부르는 API 가 아닙니다.** 로그인 대신 서버 간 공유 키"
                    + "(`X-Internal-Api-Key`)로 인증하며, 키는 환경변수 `INTERNAL_API_KEY` 값입니다.\n\n"
                    + "같은 서버 안의 도메인은 이 API 대신 `NotificationCreateUseCase` 를 직접 호출하세요.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<NotificationResponse>> create(
            @Parameter(description = "서버 간 공유 키. 환경변수 INTERNAL_API_KEY 와 같은 값", required = true)
            @RequestHeader(InternalCallGuard.HEADER) String internalApiKey,
            @Valid @RequestBody InternalNotificationCreateRequest request
    ) {
        internalCallGuard.verify(internalApiKey);

        NotificationResponse data = NotificationResponse.from(
                notificationCreateUseCase.create(request.toCommand()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("NOTIFICATION_CREATED", "알림을 생성했습니다.", data));
    }
}
