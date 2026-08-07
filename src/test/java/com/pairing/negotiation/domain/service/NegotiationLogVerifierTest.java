package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.NegotiationMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 해시 체인 검증 단위 테스트: 정상 체인은 통과, 한 줄만 조작해도 탐지된다. */
class NegotiationLogVerifierTest {

    private NegotiationMessage sealed(String content, String prevHash) {
        NegotiationMessage m = NegotiationMessage.system(1L, 1, content);
        m.seal(prevHash);
        return m;
    }

    @Test
    @DisplayName("정상 체인은 valid")
    void validChain() {
        NegotiationMessage m1 = sealed("첫 안내", NegotiationMessage.GENESIS_HASH);
        NegotiationMessage m2 = sealed("둘째 안내", m1.getContentHash());
        NegotiationMessage m3 = sealed("셋째 안내", m2.getContentHash());

        NegotiationLogVerifier.Result result = NegotiationLogVerifier.verify(List.of(m1, m2, m3));

        assertThat(result.valid()).isTrue();
        assertThat(result.checked()).isEqualTo(3);
        assertThat(result.brokenAtMessageId()).isNull();
    }

    @Test
    @DisplayName("과거 로그 내용을 조작하면 해시가 어긋나 탐지된다")
    void tamperingDetected() {
        NegotiationMessage m1 = sealed("원본 내용", NegotiationMessage.GENESIS_HASH);
        NegotiationMessage m2 = sealed("둘째 안내", m1.getContentHash());

        // DB 를 직접 고쳐 content 만 바꾸고 저장 해시는 그대로 둔 상황(조작). id=10 로 복원.
        NegotiationMessage tampered = NegotiationMessage.reconstitute(
                10L, m1.getNegotiationId(), m1.getConditionId(), m1.getRoundNo(),
                m1.getSenderType(), m1.getMessageType(),
                "조작된 내용",                       // content 변조
                m1.getReason(), m1.getProposedValue(), m1.getResponse(), m1.getActingAccountId(),
                m1.getPrevHash(), m1.getContentHash(),  // 저장 해시는 원본 그대로
                m1.getCreatedAt());

        NegotiationLogVerifier.Result result = NegotiationLogVerifier.verify(List.of(tampered, m2));

        assertThat(result.valid()).isFalse();
        assertThat(result.brokenAtMessageId()).isEqualTo(10L);
    }
}
