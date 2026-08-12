package com.pairing.contract.application.service;

import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계약이 체결되면 1:1 채팅방을 연다.
 *
 * <p><b>커밋 뒤로 미루는 이유.</b> {@code ChatCommandService} 는 {@code @Transactional} 이라
 * 체결 트랜잭션에 그대로 합류한다. 협상 당사자 조회가 비면 거기서 예외가 나는데, 그러면 스프링이
 * 공유 트랜잭션을 rollback-only 로 마킹해서 <b>서명까지 되돌아간다.</b> 호출부에서 예외를 삼켜도
 * 마킹은 남아 커밋 시점에 {@code UnexpectedRollbackException} 으로 터진다.
 *
 * <p>채팅방이 안 열린다고 계약이 무효가 되지는 않는다. 알림을 {@code try/catch} 로 감싼 것과 같은
 * 판단이라, 체결을 먼저 확정하고 방은 그 뒤에 연다. 반대로 <b>계약이 롤백되면 방도 안 생긴다</b> —
 * 별도 트랜잭션으로 미리 열면 계약 없는 방이 남는다.
 *
 * <p>{@code REQUIRES_NEW} 를 함께 거는 이유는 커밋이 끝난 트랜잭션에는 더 쓸 수 없어서다.
 * 방 개설은 자기 트랜잭션에서 처리한다.
 *
 * <p><b>{@code @Async} 는 붙이지 않았다.</b> INSERT 두 건이라 응답을 눈에 띄게 늦추지 않고,
 * 같은 스레드에 두면 실패가 그 요청 로그에 바로 남아 추적이 쉽다.
 *
 * <p>실패해도 삼키되 {@code log.error} 로 남긴다. 조용히 사라지면 사용자는 "채팅으로 프로젝트를
 * 시작할 수 있습니다" 알림을 받고도 방이 없는 상태가 되는데, 그 원인을 알 길이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractChatListener {

    private final ContractRepository contractRepository;
    private final ChatActivationUseCase chatActivationUseCase;

    /**
     * 이벤트에 {@code negotiationId} 가 없어 계약을 한 번 더 읽는다.
     *
     * <p>레코드에 필드를 더하면 이 이벤트를 이미 듣고 있는 매칭 쪽이 깨진다. 체결은 계약 1건당
     * 한 번뿐이라 조회 한 번을 감수하는 편이 낫다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ContractSignedEvent event) {
        try {
            Long negotiationId = contractRepository.findById(event.contractId())
                    .map(Contract::getNegotiationId)
                    .orElse(null);

            if (negotiationId == null) {
                log.error("채팅방을 열지 못했다. 계약을 찾을 수 없다. contractId={}", event.contractId());
                return;
            }

            // 이미 있으면 아무 일도 하지 않는다(멱등). 재시도해도 방이 두 개 생기지 않는다.
            chatActivationUseCase.openForSignedContract(negotiationId);

        } catch (Exception e) {
            log.error("채팅방 개설에 실패했다. 계약 체결은 처리됐다. contractId={}", event.contractId(), e);
        }
    }
}
