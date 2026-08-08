package com.pairing.chat.application.service;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.repository.ClientProfileRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.result.ChatRoomView;
import com.pairing.chat.application.usecase.ChatCommandUseCase;
import com.pairing.chat.application.usecase.ChatQueryUseCase;
import com.pairing.chat.domain.model.ChatMemberRole;
import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.repository.NegotiationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 채팅 도메인 검증: 타결 프로비저닝 → 방·참여자·시스템 메시지, 전송/안읽음/읽음/나가기 규칙. */
@SpringBootTest
@Transactional
class ChatServiceTest {

    @Autowired
    private ChatCommandUseCase commandUseCase;
    @Autowired
    private ChatQueryUseCase queryUseCase;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private NegotiationRepository negotiationRepository;
    @Autowired
    private ClientProfileRepository clientProfileRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Long CLIENT_ACCOUNT_ID = 920_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 920_002L;
    private static final Long PROJECT_ID = 8200L;

    private Long negotiationId;

    @BeforeEach
    void setUp() {
        Long clientProfileId = clientProfileRepository.save(ClientProfile.create(
                CLIENT_ACCOUNT_ID, "삼성전자", "1234567890",
                BusinessField.IT_CONTENTS_AI, EmployeeCount.SIZE_50_299)).getId();
        Long freelancerProfileId = freelancerProfileRepository.save(
                FreelancerProfile.create(FREELANCER_ACCOUNT_ID, LocalDate.of(1990, 1, 1))).getId();

        jdbcTemplate.update("INSERT INTO project (id, client_id, title, start_negotiable) VALUES (?, ?, ?, ?)",
                PROJECT_ID, clientProfileId, "페어링 웹 리뉴얼", true);

        Negotiation negotiation = Negotiation.create(200L, PROJECT_ID, 10L, freelancerProfileId, 5_000_000L,
                List.of(NegotiationCondition.create(ConditionType.AMOUNT, "4000000", "6000000", 0)));
        negotiationId = negotiationRepository.save(negotiation).getId();
    }

    private Long provisionRoom() {
        commandUseCase.provisionForAgreedNegotiation(negotiationId);
        return chatRoomRepository.findByNegotiationId(negotiationId).orElseThrow().getId();
    }

    @Test
    @DisplayName("타결 프로비저닝: 방 + 양측 참여자 + 시스템 메시지 생성, 입력창 활성화")
    void provisionCreatesRoomAndMembers() {
        Long roomId = provisionRoom();

        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        assertThat(room.isInputEnabled()).isTrue();
        assertThat(room.getMembers()).hasSize(2);
        assertThat(room.findMember(CLIENT_ACCOUNT_ID)).get()
                .extracting(m -> m.getRole()).isEqualTo(ChatMemberRole.CLIENT);
        assertThat(room.findMember(FREELANCER_ACCOUNT_ID)).get()
                .extracting(m -> m.getRole()).isEqualTo(ChatMemberRole.FREELANCER);

        // 방 생성 안내(시스템 메시지) 1건이 최신 메시지로 잡힌다.
        ChatRoomView view = queryUseCase.getRoom(roomId, FREELANCER_ACCOUNT_ID);
        assertThat(view.lastMessage()).contains("타결");
        assertThat(view.counterpartName()).isEqualTo("삼성전자");   // 프리 뷰어 → 상대(클라 회사명)
    }

    @Test
    @DisplayName("멱등: 이미 방이 있으면 재프로비저닝해도 방은 하나")
    void provisionIsIdempotent() {
        provisionRoom();
        commandUseCase.provisionForAgreedNegotiation(negotiationId);

        assertThat(queryUseCase.findMyRooms(CLIENT_ACCOUNT_ID)).hasSize(1);
    }

    @Test
    @DisplayName("메시지 전송: 저장·mine 표시, 상대의 안읽음 수 증가")
    void sendMessageAndUnread() {
        Long roomId = provisionRoom();

        ChatMessageView sent = commandUseCase.sendMessage(roomId, CLIENT_ACCOUNT_ID, "안녕하세요, 일정 조율 가능할까요?");
        assertThat(sent.mine()).isTrue();
        assertThat(sent.message().getContent()).isEqualTo("안녕하세요, 일정 조율 가능할까요?");

        // 프리랜서 관점: 시스템 안내 + 클라 메시지 = 2건 미읽음. 보낸 클라는 0건.
        assertThat(queryUseCase.countTotalUnread(FREELANCER_ACCOUNT_ID)).isEqualTo(2);
        assertThat(queryUseCase.countTotalUnread(CLIENT_ACCOUNT_ID)).isZero();

        var page = queryUseCase.findMessages(roomId, FREELANCER_ACCOUNT_ID,
                PageRequest.of(0, 30, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).mine()).isFalse();   // 프리 관점, 클라 메시지
    }

    @Test
    @DisplayName("읽음 처리: 안읽음 수가 0이 된다")
    void markAsReadClearsUnread() {
        Long roomId = provisionRoom();
        commandUseCase.sendMessage(roomId, CLIENT_ACCOUNT_ID, "확인 부탁드려요");
        assertThat(queryUseCase.countTotalUnread(FREELANCER_ACCOUNT_ID)).isEqualTo(2);

        commandUseCase.markAsRead(roomId, FREELANCER_ACCOUNT_ID);

        assertThat(queryUseCase.countTotalUnread(FREELANCER_ACCOUNT_ID)).isZero();
    }

    @Test
    @DisplayName("입력창 활성 방에서 참여자가 아니면 전송 거부(NOT_PARTICIPANT)")
    void nonParticipantCannotSend() {
        Long roomId = provisionRoom();
        Long stranger = 999_999L;

        assertThatThrownBy(() -> commandUseCase.sendMessage(roomId, stranger, "hi"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.NOT_PARTICIPANT);
    }

    @Test
    @DisplayName("나가기: 방이 진행 중(ACTIVE)이면 나갈 수 없다(LEAVE_NOT_ALLOWED)")
    void cannotLeaveActiveRoom() {
        Long roomId = provisionRoom();

        assertThatThrownBy(() -> commandUseCase.leave(roomId, CLIENT_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ChatErrorCode.LEAVE_NOT_ALLOWED);
    }
}
