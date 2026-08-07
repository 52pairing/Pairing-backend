package com.pairing.chat.presentation.api;

import com.pairing.chat.domain.model.ChatMessageType;
import com.pairing.chat.domain.model.ChatRoomStatus;
import com.pairing.chat.presentation.api.request.ChatMessageSendRequest;
import com.pairing.chat.presentation.api.response.ChatMessageResponse;
import com.pairing.chat.presentation.api.response.ChatRoomResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.chat.presentation.api.response.ChatUnreadCountResponse;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 협상 후 사람 채팅. (요구사항 R23)
 *
 * <p>협상 중에는 입력창이 잠겨 있고 A2A 응답만 가능하다. 협상이 완료되면 입력창이 열린다.
 * 실시간 수신은 STOMP(/ws)를 쓰고, 이 REST API 는 목록·이력 조회와 전송을 담당한다.
 *
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "13. Chat", description = "채팅 API")
public class ChatController {

    @GetMapping
    @Operation(summary = "내 채팅방 목록", description = "메인 헤더의 메시지 목록에 사용합니다.")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> findMyRooms(@CurrentAccountId Long accountId) {
        // TODO: 내가 참여 중인 방 목록 (마지막 메시지·안읽음 수 포함)
        return ResponseEntity.ok(ApiResponse.success("CHAT_ROOMS_FOUND", "조회에 성공했습니다.", List.of(sampleRoom())));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안 읽은 메시지 수",
            description = "헤더 메시지 아이콘의 배지 숫자입니다. 모든 채팅방의 미읽음 합계를 반환합니다.")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponse>> findUnreadCount(
            @CurrentAccountId Long accountId
    ) {
        // TODO: 내가 속한 방의 last_read_at 이후 메시지 수 합산
        return ResponseEntity.ok(ApiResponse.success("UNREAD_COUNT_FOUND", "조회에 성공했습니다.",
                new ChatUnreadCountResponse(3)));
    }

    @GetMapping("/{chatRoomId}")
    @Operation(summary = "채팅방 상세")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ChatRoomResponse>> findRoom(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 참여자만 열람
        return ResponseEntity.ok(ApiResponse.success("CHAT_ROOM_FOUND", "조회에 성공했습니다.", sampleRoom()));
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "메시지 목록", description = "최신순 페이지 조회입니다. 과거 메시지는 page 를 늘려 가져옵니다.")
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> findMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 메시지 페이지 조회
        return ResponseEntity.ok(ApiResponse.success("MESSAGES_FOUND", "조회에 성공했습니다.",
                new PageResponse<>(List.of(sampleMessage()), page, size, 1, 1, true, true)));
    }

    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "메시지 전송",
            description = "협상이 완료되어 입력창이 열린 방에서만 보낼 수 있습니다. 한 번에 500자까지입니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST", "ACCESS_DENIED"})
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatMessageSendRequest request,
            @CurrentAccountId Long accountId
    ) {
        // TODO: input_enabled 확인 후 저장 + STOMP 브로드캐스트
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("MESSAGE_SENT", "전송했습니다.", sampleMessage()));
    }

    @PostMapping("/{chatRoomId}/read")
    @Operation(summary = "읽음 처리", description = "마지막 읽은 시각을 현재로 갱신합니다.")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: chat_room_member.last_read_at 갱신
        return ResponseEntity.ok(ApiResponse.success("CHAT_READ", "읽음 처리했습니다."));
    }

    @PostMapping("/{chatRoomId}/leave")
    @Operation(summary = "채팅방 나가기",
            description = "협상 결렬 후 또는 대금 지급이 끝난 뒤에만 나갈 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 나가기 가능 조건 확인 후 left_at 기록
        return ResponseEntity.ok(ApiResponse.success("CHAT_LEFT", "채팅방에서 나갔습니다."));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private ChatRoomResponse sampleRoom() {
        return new ChatRoomResponse(500L, 300L, "페어링 웹 리뉴얼", "홍길동", ChatRoomStatus.ACTIVE,
                true, false, "안녕하세요", LocalDateTime.now(), 2);
    }

    private ChatMessageResponse sampleMessage() {
        return new ChatMessageResponse(1000L, 3L, "홍길동", false, ChatMessageType.TEXT,
                "안녕하세요, 일정 조율 가능할까요?", LocalDateTime.now());
    }
}
