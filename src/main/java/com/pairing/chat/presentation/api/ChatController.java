package com.pairing.chat.presentation.api;

import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.usecase.ChatCommandUseCase;
import com.pairing.chat.application.usecase.ChatQueryUseCase;
import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.chat.presentation.api.request.ChatMessageSendRequest;
import com.pairing.chat.presentation.api.response.ChatMessageResponse;
import com.pairing.chat.presentation.api.response.ChatRoomResponse;
import com.pairing.chat.presentation.api.response.ChatUnreadCountResponse;
import com.pairing.chat.presentation.api.support.ChatResponseFactory;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.common.api.response.PageResponse;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 협상 후 사람 채팅. (요구사항 R23)
 *
 * <p>협상 중에는 입력창이 잠겨 있고 A2A 응답만 가능하다. 협상이 타결되면 방이 열리고 입력창이 활성화된다.
 * 실시간 수신은 STOMP({@code /topic/chat-rooms/{id}})를 쓰고, 이 REST API 는 목록·이력 조회와
 * 전송·읽음·나가기를 담당한다.
 */
@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
@Tag(name = "13. Chat", description = "채팅 API")
public class ChatController {

    private final ChatQueryUseCase chatQueryUseCase;
    private final ChatCommandUseCase chatCommandUseCase;

    @GetMapping
    @Operation(summary = "내 채팅방 목록", description = "메인 헤더의 메시지 목록에 사용합니다. 마지막 메시지·안읽음 수를 포함합니다.")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> findMyRooms(@CurrentAccountId Long accountId) {
        List<ChatRoomResponse> rooms = chatQueryUseCase.findMyRooms(accountId).stream()
                .map(ChatResponseFactory::room)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("CHAT_ROOMS_FOUND", "조회에 성공했습니다.", rooms));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "안 읽은 메시지 수",
            description = "헤더 메시지 아이콘의 배지 숫자입니다. 모든 채팅방의 미읽음 합계를 반환합니다.")
    public ResponseEntity<ApiResponse<ChatUnreadCountResponse>> findUnreadCount(@CurrentAccountId Long accountId) {
        int unread = chatQueryUseCase.countTotalUnread(accountId);
        return ResponseEntity.ok(ApiResponse.success("UNREAD_COUNT_FOUND", "조회에 성공했습니다.",
                new ChatUnreadCountResponse(unread)));
    }

    @GetMapping("/by-negotiation/{negotiationId}")
    @Operation(summary = "협상으로 채팅방 조회",
            description = "협상 타결 후 '채팅으로 이어가기' 이동에 사용합니다. 해당 협상의 채팅방을 반환합니다.")
    @ApiErrorCodeExample(domain = ChatErrorCode.class, value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT"})
    public ResponseEntity<ApiResponse<ChatRoomResponse>> findRoomByNegotiation(
            @PathVariable Long negotiationId,
            @CurrentAccountId Long accountId
    ) {
        ChatRoomResponse room = ChatResponseFactory.room(
                chatQueryUseCase.getRoomByNegotiation(negotiationId, accountId));
        return ResponseEntity.ok(ApiResponse.success("CHAT_ROOM_FOUND", "조회에 성공했습니다.", room));
    }

    @GetMapping("/{chatRoomId}")
    @Operation(summary = "채팅방 상세")
    @ApiErrorCodeExample(domain = ChatErrorCode.class, value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT"})
    public ResponseEntity<ApiResponse<ChatRoomResponse>> findRoom(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        ChatRoomResponse room = ChatResponseFactory.room(chatQueryUseCase.getRoom(chatRoomId, accountId));
        return ResponseEntity.ok(ApiResponse.success("CHAT_ROOM_FOUND", "조회에 성공했습니다.", room));
    }

    @GetMapping("/{chatRoomId}/messages")
    @Operation(summary = "메시지 목록", description = "최신순 페이지 조회입니다. 과거 메시지는 page 를 늘려 가져옵니다.")
    @ApiErrorCodeExample(domain = ChatErrorCode.class, value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT"})
    public ResponseEntity<ApiResponse<PageResponse<ChatMessageResponse>>> findMessages(
            @PathVariable Long chatRoomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @CurrentAccountId Long accountId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        PageResponse<ChatMessageResponse> result = PageResponse.from(
                chatQueryUseCase.findMessages(chatRoomId, accountId, pageable)
                        .map(ChatResponseFactory::message));
        return ResponseEntity.ok(ApiResponse.success("MESSAGES_FOUND", "조회에 성공했습니다.", result));
    }

    @PostMapping("/{chatRoomId}/messages")
    @Operation(summary = "메시지 전송",
            description = "협상이 타결되어 입력창이 열린 방에서만 보낼 수 있습니다. 한 번에 500자까지입니다.")
    @ApiErrorCodeExample(domain = ChatErrorCode.class,
            value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT", "INPUT_DISABLED", "ALREADY_LEFT"})
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody ChatMessageSendRequest request,
            @CurrentAccountId Long accountId
    ) {
        ChatMessageView sent = chatCommandUseCase.sendMessage(chatRoomId, accountId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("MESSAGE_SENT", "전송했습니다.", ChatResponseFactory.message(sent)));
    }

    @PostMapping("/{chatRoomId}/read")
    @Operation(summary = "읽음 처리", description = "마지막 읽은 시각을 현재로 갱신합니다.")
    @ApiErrorCodeExample(domain = ChatErrorCode.class, value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT", "ALREADY_LEFT"})
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        chatCommandUseCase.markAsRead(chatRoomId, accountId);
        return ResponseEntity.ok(ApiResponse.success("CHAT_READ", "읽음 처리했습니다."));
    }

    @PostMapping("/{chatRoomId}/leave")
    @Operation(summary = "채팅방 나가기",
            description = "협상 결렬 후 또는 대금 지급이 끝나 방이 종료된 뒤에만 나갈 수 있습니다.")
    @ApiErrorCodeExample(domain = ChatErrorCode.class,
            value = {"CHAT_ROOM_NOT_FOUND", "NOT_PARTICIPANT", "LEAVE_NOT_ALLOWED", "ALREADY_LEFT"})
    public ResponseEntity<ApiResponse<Void>> leave(
            @PathVariable Long chatRoomId,
            @CurrentAccountId Long accountId
    ) {
        chatCommandUseCase.leave(chatRoomId, accountId);
        return ResponseEntity.ok(ApiResponse.success("CHAT_LEFT", "채팅방에서 나갔습니다."));
    }
}
