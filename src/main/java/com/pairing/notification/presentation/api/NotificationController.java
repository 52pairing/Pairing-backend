package com.pairing.notification.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.notification.presentation.api.response.NotificationResponse;
import com.pairing.notification.presentation.api.response.UnreadCountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림. (요구사항 R27)
 *
 * <p>상태 변경(읽음)은 PUT, 삭제는 DELETE 로 처리한다.
 * 알림을 누르면 linkUrl 로 이동하므로 화면 경로는 서버가 정해서 내려준다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "17. Notification", description = "알림 API")
public class NotificationController {

    @GetMapping
    @Operation(summary = "알림 목록", description = "unreadOnly=true 면 읽지 않은 알림만 반환합니다.")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> findMine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내 알림 최신순 조회
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sample()), page, size, 1, 1, true, true)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수", description = "헤더 배지에 사용합니다.")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> countUnread(@CurrentAccountId Long accountId) {
        // TODO: 안읽음 수 집계
        return ResponseEntity.ok(ApiResponse.success("UNREAD_COUNT_FOUND", "조회에 성공했습니다.",
                new UnreadCountResponse(3)));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 본인 알림인지 확인 후 읽음 처리
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_READ", "읽음 처리했습니다."));
    }

    @PutMapping("/read-all")
    @Operation(summary = "알림 모두 읽음 처리")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@CurrentAccountId Long accountId) {
        // TODO: 내 알림 전체 읽음 처리
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_READ", "모두 읽음 처리했습니다."));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "알림 삭제")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long notificationId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 본인 알림인지 확인 후 삭제
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATION_DELETED", "삭제했습니다."));
    }

    @DeleteMapping
    @Operation(summary = "알림 모두 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteAll(@CurrentAccountId Long accountId) {
        // TODO: 내 알림 전체 삭제
        return ResponseEntity.ok(ApiResponse.success("NOTIFICATIONS_DELETED", "모두 삭제했습니다."));
    }

    private NotificationResponse sample() {
        return new NotificationResponse(1100L, NotificationType.MATCHING_REQUESTED,
                "매칭 요청이 도착했습니다", "페어링 웹 리뉴얼 프로젝트에서 매칭 요청을 보냈습니다.",
                "/matchings/requests/200", false, LocalDateTime.now());
    }
}
