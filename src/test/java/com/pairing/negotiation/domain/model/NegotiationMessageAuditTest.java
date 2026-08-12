package com.pairing.negotiation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 감사 기록이 당사자 화면에서 걸러지는지 검증한다.
 *
 * <p>타결 시점 최종 조건 스냅샷은 기계가 파싱할 증거라 사람이 읽을 물건이 아닌데, 그동안
 * 일반 시스템 안내와 똑같이 협상방에 노출됐다. 공백 없는 한 줄이라 화면을 옆으로 밀어
 * 가로 스크롤까지 만들었다(2026-08-12).
 *
 * <p>DB 의 {@code message_type} CHECK 제약 때문에 별도 타입을 못 만들고 내용 표식으로
 * 구분한다. <b>표식 방식은 조용히 깨지기 쉬우므로</b> — 누가 content 조립을 바꾸면 그냥
 * 다시 노출된다 — 테스트로 고정한다.
 */
class NegotiationMessageAuditTest {

    @Test
    @DisplayName("감사 기록은 audit 으로 표시되고 사람이 읽을 안내는 아니다")
    void auditIsDistinguishedFromSystemNotice() {
        NegotiationMessage audit = NegotiationMessage.audit(1L, 2,
                "최종 조건 봉인: agreedAmount=3300000|AMOUNT=3300000|WORK_STYLE=ONSITE");
        NegotiationMessage notice = NegotiationMessage.system(1L, 2,
                "마지노선이 저장되었습니다. 상대방이 조건을 입력하면 AI 대리인 협상이 시작됩니다.");

        assertThat(audit.isAudit()).isTrue();
        assertThat(notice.isAudit()).isFalse();
    }

    @Test
    @DisplayName("DB CHECK 제약을 지키려고 messageType 은 SYSTEM 을 그대로 쓴다")
    void auditKeepsAllowedMessageType() {
        NegotiationMessage audit = NegotiationMessage.audit(1L, 2, "최종 조건 봉인: AMOUNT=3300000");

        // RDS 에 message_type CHECK IN ('PROPOSAL','RESPONSE','SYSTEM') 이 걸려 있다.
        // 여기가 깨지면 INSERT 가 거부돼 타결 트랜잭션이 통째로 롤백된다.
        assertThat(audit.getMessageType()).isEqualTo(NegotiationMessageType.SYSTEM);
        assertThat(audit.getSenderType()).isEqualTo(SenderType.SYSTEM);
    }

    @Test
    @DisplayName("사용자가 입력한 내용은 감사 기록으로 오인되지 않는다")
    void userContentIsNeverMistakenForAudit() {
        // 표식은 유닛 세퍼레이터(제어문자)라 사람이 타이핑할 수 없다.
        NegotiationMessage looksLikeAudit = NegotiationMessage.system(1L, 1,
                "최종 조건 봉인: agreedAmount=3300000");

        assertThat(looksLikeAudit.isAudit()).isFalse();
    }

    @Test
    @DisplayName("감사 기록도 해시 체인에 들어간다 — 증거 능력은 그대로다")
    void auditIsStillSealedIntoChain() {
        NegotiationMessage audit = NegotiationMessage.audit(1L, 2, "최종 조건 봉인: AMOUNT=3300000");

        audit.seal(NegotiationMessage.GENESIS_HASH);

        assertThat(audit.getContentHash()).isNotBlank();
        assertThat(audit.recomputeHash()).isEqualTo(audit.getContentHash());
    }
}
