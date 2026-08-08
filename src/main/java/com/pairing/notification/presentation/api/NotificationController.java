package com.pairing.notification.presentation.api;

import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.notification.application.usecase.NotificationUseCase;
import com.pairing.notification.exception.NotificationErrorCode;
import com.pairing.notification.presentation.api.response.NotificationResponse;
import com.pairing.notification.presentation.api.response.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림. (요구사항 R27)
 *
 * <p>상태 변경(읽음)은 PUT, 삭제는 DELETE 로 처리한다.
 * 알림을 누르면 linkUrl 로 이동하므로 화면 경로는 서버가 정해서 내려준다.
 *
 * <p>알림을 실제로 만드는 지점(매칭 요청 도착, 협상 시작 등)은 각 도메인이 소유한다.
 * 이 컨트롤러는 로그인 계정 본인의 알림 조회·읽음·삭제만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "17. Notification", description = "알림 API")
public class NotificationController {

    private final NotificationUseCase notificationUseCase;

    @GetMapping
    @Operation(summary = "알림 목록", description = "unreadOnly=true 면 읽지 않은 알림만 반환합니다.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> findMine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<NotificationResponse> response = PageResponse.from(
                notificationUseCase.findMine(accountId, unreadOnly, pageable).map(NotificationResponse::from));
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_FOUND", "조회에 성공했습니다.", response));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수", description = "헤더 배지에 사용합니다.")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> countUnread(@CurrentAccountId Long accountId) {
        return ResponseEntity.ok(ApiResponse.success("UNREAD_COUNT_FOUND", "조회에 성공했습니다.",
                new UnreadCountResponse(notificationUseCase.countUnread(accountId))));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    @ApiErrorCodeExample(domain = NotificationErrorCode.class, value = {"NOTIFICATION_NOT_FOUND", "NOTIFICATION_FORBIDDEN"})
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @CurrentAccountId Long accountId
    ) {
        notificationUseCase.markAsRead(accountId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_READ", "읽음 처리했습니다."));
    }

    @PutMapping("/read-all")
    @Operation(summary = "알림 모두 읽음 처리")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@CurrentAccountId Long accountId) {
        notificationUseCase.markAllAsRead(accountId);
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_READ", "모두 읽음 처리했습니다."));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "알림 삭제")
    @ApiErrorCodeExample(domain = NotificationErrorCode.class, value = {"NOTIFICATION_NOT_FOUND", "NOTIFICATION_FORBIDDEN"})
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long notificationId,
            @CurrentAccountId Long accountId
    ) {
        notificationUseCase.delete(accountId, notificationId);
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_DELETED", "삭제했습니다."));
    }

    @DeleteMapping
    @Operation(summary = "알림 모두 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteAll(@CurrentAccountId Long accountId) {
        notificationUseCase.deleteAll(accountId);
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_DELETED", "모두 삭제했습니다."));
    }
}
