package com.pairing.negotiation.domain.service;

import com.pairing.negotiation.domain.model.NegotiationMessage;

import java.util.List;

/**
 * 협상 로그 해시 체인 검증. 각 로그의 저장 해시를 재계산해 대조하고, 직전 해시 연결이 끊기지
 * 않았는지 본다. 과거 로그를 조작하면 그 지점부터 체인이 어긋나 valid=false 로 드러난다.
 */
public final class NegotiationLogVerifier {

    private NegotiationLogVerifier() {
    }

    /** @param brokenAtMessageId 무결성이 깨진 첫 로그 ID(정상이면 null) */
    public record Result(boolean valid, Long brokenAtMessageId, int checked) {
    }

    /** ordered = id(=append) 순으로 정렬된 로그 전체. */
    public static Result verify(List<NegotiationMessage> ordered) {
        String expectedPrev = NegotiationMessage.GENESIS_HASH;
        int checked = 0;

        for (NegotiationMessage message : ordered) {
            checked++;
            boolean linkOk = expectedPrev.equals(message.getPrevHash());
            boolean hashOk = message.getContentHash() != null
                    && message.getContentHash().equals(message.recomputeHash());
            if (!linkOk || !hashOk) {
                return new Result(false, message.getId(), checked);
            }
            expectedPrev = message.getContentHash();
        }
        return new Result(true, null, checked);
    }
}
