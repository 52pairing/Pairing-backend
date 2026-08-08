package com.pairing.matching.infrastructure.negotiation;

import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.result.NegotiationSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link NegotiationPort}의 임시 구현.
 *
 * <p>negotiation 도메인에 아직 인바운드 application 계층이 없어(2026-08-07 기준 domain/presentation만 존재)
 * 실제 협상방을 만들 수 없다. 수락 자체는 유효한 사용자 행동이므로 실패로 롤백시키지 않고,
 * 눈에 띄는 음수 placeholder ID를 돌려준다 — 실제 협상ID와 절대 혼동되지 않게 하기 위함이다.
 * negotiation팀이 실제 UseCase를 만들면 이 클래스를 그 UseCase 위임 호출로 교체한다.
 */
@Slf4j
@Component
public class StubNegotiationAdapter implements NegotiationPort {

    @Override
    public Long createNegotiation(CreateNegotiationCommand command) {
        log.warn("[협상 미연동 스텁] negotiation 도메인 인바운드 포트가 아직 없어 임시 협상ID를 반환합니다. requestId={}",
                command.requestId());
        return -command.requestId();
    }

    @Override
    public Optional<NegotiationSummary> findSummaryByRequestId(Long requestId) {
        return Optional.empty();
    }
}
