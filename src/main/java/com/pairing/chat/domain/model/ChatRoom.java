package com.pairing.chat.domain.model;

import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 협상 후 사람 채팅방. 협상 1건당 방 하나가 생긴다(negotiation_id UNIQUE).
 *
 * <p>방이 생기는 시점은 <b>계약 체결</b>이다. 협상 중에는 A2A 응답(숫자·선택지)만 가능하고, 협상이
 * 타결돼도 계약이 체결되기 전까지는 방이 없다. 프로젝트 진행 대화는 계약이 성립한 뒤에 시작하기 때문이며,
 * 계약이 무산되면 빈 방이 남지 않는다.
 *
 * <p>나가기는 대금 지급 완료 등으로 방이 종료(CLOSED)된 뒤에만 허용한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    private Long id;
    private Long negotiationId;
    private boolean inputEnabled;
    private ChatRoomStatus status;
    private List<ChatRoomMember> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    private ChatRoom(Long id, Long negotiationId, boolean inputEnabled, ChatRoomStatus status,
                     List<ChatRoomMember> members, LocalDateTime createdAt, LocalDateTime updatedAt,
                     LocalDateTime closedAt) {
        this.id = id;
        this.negotiationId = negotiationId;
        this.inputEnabled = inputEnabled;
        this.status = status;
        this.members = members;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
    }

    /** 계약 체결 시 방을 연다. 이 시점부터 대화가 가능하므로 입력창은 바로 활성화된다. */
    public static ChatRoom open(Long negotiationId, List<ChatRoomMember> members) {
        LocalDateTime now = LocalDateTime.now();
        return new ChatRoom(null, negotiationId, true, ChatRoomStatus.ACTIVE, members, now, now, null);
    }

    public static ChatRoom reconstitute(Long id, Long negotiationId, boolean inputEnabled,
                                        ChatRoomStatus status, List<ChatRoomMember> members,
                                        LocalDateTime createdAt, LocalDateTime updatedAt,
                                        LocalDateTime closedAt) {
        return new ChatRoom(id, negotiationId, inputEnabled, status, members, createdAt, updatedAt, closedAt);
    }

    /** 방 종료(대금 지급 완료 등). 종료 후에만 나가기가 허용된다. */
    public void close() {
        this.status = ChatRoomStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.updatedAt = this.closedAt;
    }

    public Optional<ChatRoomMember> findMember(Long accountId) {
        return members.stream().filter(m -> m.isOwnedBy(accountId)).findFirst();
    }

    /** 참여자면 통과(나간 사람 포함 = 지난 대화 열람 허용). 아니면 NOT_PARTICIPANT. */
    public ChatRoomMember requireParticipant(Long accountId) {
        return findMember(accountId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPANT));
    }

    /** 참여자(나가지 않은)만 통과. 아니면 NOT_PARTICIPANT / ALREADY_LEFT. */
    public ChatRoomMember requireActiveMember(Long accountId) {
        ChatRoomMember member = findMember(accountId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.NOT_PARTICIPANT));
        if (member.hasLeft()) {
            throw new BusinessException(ChatErrorCode.ALREADY_LEFT);
        }
        return member;
    }

    /** 상대 참여자(나 아닌 사람). 표시용 이름 결정에 쓴다. */
    public Optional<ChatRoomMember> counterpartOf(Long accountId) {
        return members.stream().filter(m -> !m.isOwnedBy(accountId)).findFirst();
    }

    /** 메시지 전송 가능 조건 검증: 참여자 + 입력창 활성화. */
    public void ensureCanSend(Long accountId) {
        requireActiveMember(accountId);
        if (!inputEnabled) {
            throw new BusinessException(ChatErrorCode.INPUT_DISABLED);
        }
    }

    /** 나가기 가능 여부. 방이 종료(CLOSED)된 뒤에만 나갈 수 있다. */
    public boolean isLeaveAllowed() {
        return status == ChatRoomStatus.CLOSED;
    }
}
