package com.pairing.chat.application.service;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.chat.application.usecase.ChatActivationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 체결됐는데 열리지 않은 채팅방을 <b>조회 시점에</b> 되살린다.
 *
 * <p>방은 계약 체결과 함께 열린다({@code ContractChatListener}). 그 개설이 실패하면 계약은 체결됐는데
 * 대화할 방이 없는 상태로 남고, 지금까지는 그걸 되돌릴 방법이 없었다. 사용자가 채팅에 들어오는
 * 순간 복구하면 <b>재처리 배치가 필요 없다.</b> 방 개설은 이미 멱등이라 두 번 열리지 않는다.
 *
 * <p><b>왜 별도 빈인가.</b> 조회는 {@code readOnly} 트랜잭션이라 그 안에서는 쓸 수 없다. 새
 * 트랜잭션이 필요하고, {@code @Transactional} 은 프록시로 동작해서 같은 빈 안에서 자기 메서드를
 * 부르면(self-invocation) 무시된다.
 *
 * <p><b>예외는 여기서 삼키지 않는다.</b> 안에서 잡아도 이 트랜잭션이 이미 rollback-only 로
 * 찍혀 있으면 경계에서 {@code UnexpectedRollbackException} 이 다시 난다. 삼키는 자리는 호출부인
 * {@code ChatQueryService} 다 — 복구가 실패해도 조회 자체는 성공해야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ChatRoomRepairer {

    private final ChatDirectoryPort chatDirectoryPort;
    private final ChatActivationUseCase chatActivationUseCase;

    /** 채팅 목록 진입 시. 내 협상 중 체결됐는데 방이 없는 건을 전부 연다. 정상이면 아무 일도 없다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void repairMyRooms(Long accountId) {
        List<Long> missing = chatDirectoryPort.findNegotiationIdsMissingRoom(accountId);
        for (Long negotiationId : missing) {
            log.warn("체결된 계약인데 채팅방이 없어 조회 시점에 연다. negotiationId={}, accountId={}",
                    negotiationId, accountId);
            chatActivationUseCase.openForSignedContract(negotiationId);
        }
    }

    /**
     * 협상으로 방을 찾는 경로에서. <b>체결 여부를 반드시 확인한다</b> — 확인 없이 열면 협상만 타결된
     * 건에도 방이 생겨, 계약이 무산됐을 때 빈 방이 남는다(방을 체결 시점에 여는 이유가 그것이다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void repairIfConcluded(Long negotiationId) {
        if (!chatDirectoryPort.isContractConcluded(negotiationId)) {
            return;
        }
        log.warn("체결된 계약인데 채팅방이 없어 조회 시점에 연다. negotiationId={}", negotiationId);
        chatActivationUseCase.openForSignedContract(negotiationId);
    }
}
