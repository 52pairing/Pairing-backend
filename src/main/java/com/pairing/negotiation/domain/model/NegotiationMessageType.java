package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 협상 메시지 종류.
 *
 * <p><b>값을 늘리려면 DB 마이그레이션이 먼저다.</b> RDS 의 {@code negotiation_message} 에
 * {@code message_type CHECK IN ('PROPOSAL','RESPONSE','SYSTEM')} 제약이 걸려 있어서, 새 값을
 * 추가하면 INSERT 가 거부되고 <b>그 값을 쓰는 트랜잭션이 통째로 롤백된다</b>(타결 경로라면
 * 계약 생성까지 함께 날아간다). {@code db/init/02-create-schema.sql} 에는 이 제약이 없으니
 * 스키마 파일만 보고 판단하지 말 것 — 실제로 그렇게 판단했다가 걸렸다(2026-08-12).
 */
@Getter
@RequiredArgsConstructor
public enum NegotiationMessageType {

    PROPOSAL("AI 제안"),
    RESPONSE("사용자 응답"),
    SYSTEM("시스템 안내");

    private final String label;
}
