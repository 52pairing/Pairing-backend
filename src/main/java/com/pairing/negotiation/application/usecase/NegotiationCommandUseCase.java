package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.application.command.CreateNegotiationCommand;

/**
 * 협상 생성 인바운드 포트. 매칭 수락 처리(POST /matchings/requests/{id}/acceptance)에서
 * <b>동기</b>로 호출한다. 같은 트랜잭션에서 실행되며, 실패 시 예외를 던져 수락까지 롤백되게 한다.
 */
public interface NegotiationCommandUseCase {

    /**
     * 매칭 수락으로 협상을 생성한다. 클라(project) 희망값과 프리 스냅샷을 비교해 불일치 조건만 담는다.
     *
     * @return 생성된 협상 ID
     */
    Long create(CreateNegotiationCommand command);
}
