package com.pairing.chat.presentation.api.response;

import com.pairing.chat.domain.model.ChatRoomStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 채팅방. 협상 1건에 방 하나가 생긴다. */
@Schema(description = "채팅방 응답")
public record ChatRoomResponse(

        @Schema(description = "채팅방 ID", example = "500") Long chatRoomId,
        @Schema(description = "협상 ID", example = "300") Long negotiationId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "상대 이름", example = "홍길동") String counterpartName,
        @Schema(description = "상태") ChatRoomStatus status,
        @Schema(description = "입력창 활성화 여부. 협상 완료 후 true 가 된다.", example = "true") boolean inputEnabled,
        @Schema(description = "나가기 가능 여부. 대금 지급 완료 또는 협상 결렬 시 true", example = "false") boolean leaveEnabled,
        @Schema(description = "마지막 메시지", example = "안녕하세요") String lastMessage,
        @Schema(description = "마지막 메시지 시각") LocalDateTime lastMessageAt,
        @Schema(description = "읽지 않은 메시지 수", example = "2") int unreadCount
) {
}
