package com.pairing.chat.application.usecase;

import com.pairing.chat.application.result.ChatMessageView;
import com.pairing.chat.application.result.ChatRoomView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** 채팅 조회 인바운드 포트. */
public interface ChatQueryUseCase {

    /** 내가 참여 중(나가지 않음)인 방 목록. 마지막 메시지·안읽음 수 포함. */
    List<ChatRoomView> findMyRooms(Long accountId);

    /** 방 상세(참여자만). */
    ChatRoomView getRoom(Long chatRoomId, Long accountId);

    /** 협상 ID 로 방 조회(타결 후 "채팅으로 이어가기" 이동용, 참여자만). */
    ChatRoomView getRoomByNegotiation(Long negotiationId, Long accountId);

    /** 방 메시지 페이지(최신순, 참여자만). */
    Page<ChatMessageView> findMessages(Long chatRoomId, Long accountId, Pageable pageable);

    /** 헤더 배지용 — 내 모든 방의 안 읽은 메시지 합계. */
    int countTotalUnread(Long accountId);
}
