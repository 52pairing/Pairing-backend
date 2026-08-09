package com.pairing.contract.application.port;

import com.pairing.contract.domain.model.ContractDraftText;

import java.util.List;
import java.util.Optional;

/**
 * 계약서 자유 텍스트 정리. AI 서버 호출을 감싼다.
 *
 * <p>AI 는 보조 수단이다. 실패했다고 계약 체결이 막히면 안 되므로 예외를 던지지 않고
 * {@code Optional.empty()} 를 돌려준다. 호출부는 비어 있으면
 * {@link ContractDraftText#defaults} 로 원문을 잘라 계약서를 만든다.
 */
public interface ContractDraftPort {

    Optional<ContractDraftText> draft(ContractDraftCommand command);

    /**
     * 정리할 원문.
     *
     * <p>{@code agreedNotes} 는 협상에서 합의된 SCOPE·OTHER 조건 값이다. 비어 있으면
     * 특약사항이 없는 것으로 확정되고, AI 가 문장을 만들어내도 무시된다.
     */
    record ContractDraftCommand(Long contractId, String mainTask, String detailScope,
                                List<String> agreedNotes) {
    }
}
