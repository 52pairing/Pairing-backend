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
 * <p>입력창이 열리는 순서는 이렇다.
 * <ol>
 *   <li>협상 중: 방이 없다. A2A 응답(숫자·선택지)만 가능하다.</li>
 *   <li>협상 타결(AI Out): 방이 생기지만 <b>입력창은 잠겨 있다</b>. 합의안만 보인다.</li>
 *   <li>계약 체결: {@link #enableInput()} 으로 입력창이 열리고 프로젝트 진행 대화를 시작한다.</li>
 * </ol>
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

    /**
     * 협상 타결 시 방을 연다. <b>입력창은 잠긴 채로 열린다</b> — 계약이 체결돼야 대화를 시작할 수 있다.
     * 방을 미리 만들어 두는 이유는 타결 시점의 합의안을 방 상단에 보여 주고, 계약 체결 즉시 바로
     * 대화로 넘어가게 하기 위해서다.
     *
     * @see #enableInput()
     */
    public static ChatRoom open(Long negotiationId, List<ChatRoomMember> members) {
        LocalDateTime now = LocalDateTime.now();
        return new ChatRoom(null, negotiationId, false, ChatRoomStatus.ACTIVE, members, now, now, null);
    }

    public static ChatRoom reconstitute(Long id, Long negotiationId, boolean inputEnabled,
                                        ChatRoomStatus status, List<ChatRoomMember> members,
                                        LocalDateTime createdAt, LocalDateTime updatedAt,
                                        LocalDateTime closedAt) {
        return new ChatRoom(id, negotiationId, inputEnabled, status, members, createdAt, updatedAt, closedAt);
    }

    /**
     * 계약 체결로 입력창을 연다(계약 도메인이 {@code ChatActivationUseCase} 로 호출).
     * 이미 열려 있으면 아무 일도 하지 않는다(재호출·재시도 대비).
     *
     * @return 이번 호출로 실제 열렸으면 true. 안내 메시지를 한 번만 남기는 데 쓴다
     */
    public boolean enableInput() {
        if (inputEnabled) {
            return false;
        }
        this.inputEnabled = true;
        this.updatedAt = LocalDateTime.now();
        return true;
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
