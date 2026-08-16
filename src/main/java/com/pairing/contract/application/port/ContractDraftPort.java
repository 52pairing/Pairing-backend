package com.pairing.contract.application.port;

import com.pairing.contract.domain.model.ContractDraftText;

import java.util.List;

/**
 * 계약서 자유 텍스트 정리. AI 서버 호출을 감싼다.
 *
 * <p><b>실패하면 예외를 던진다.</b> 예전에는 {@code Optional.empty()} 를 돌려주고 호출부가
 * 원문으로 대체했는데, 그러면 재시도를 걸 자리가 없다. resilience4j 의 {@code @Retry} 는
 * 예외를 봐야 발동하고, 스케줄러도 "실패했다"를 알아야 계약을 다시 집을 수 있다.
 *
 * <p>포기 판단은 호출부({@code ContractDraftFiller})가 한다. 어댑터는 성공/실패만 말한다.
 */
public interface ContractDraftPort {

    ContractDraftText draft(ContractDraftCommand command);

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
