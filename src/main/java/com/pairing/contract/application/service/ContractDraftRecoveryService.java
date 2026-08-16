package com.pairing.contract.application.service;

import com.pairing.contract.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DRAFT 로 멈춘 계약서의 문구를 다시 채운다.
 *
 * <p>즉시 재시도({@code @Retry})는 연결 실패만 잡는다. 파이썬이 5xx 를 내려주는 경우 —
 * Gemini 키가 한도에 걸려 300초 쿨다운에 들어간 상황이 대표적이다 — 는 1초 뒤 재호출로
 * 풀리지 않는다. 5분 주기가 그 회복 시간과 맞다.
 *
 * <p>매칭의 {@code StaleRoundRecoveryService} 와 같은 문제를 푼다. 다만 거기는 한 번 더
 * 실패하면 FAILED 로 닫는데, 계약은 그럴 수 없다 — 클라이언트에게 재추천 같은 복구 수단이
 * 없다. 대신 포기 시한을 두고 그때 원문으로 확정한다({@link ContractDraftFiller}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractDraftRecoveryService {

    private final ContractRepository contractRepository;
    private final ContractDraftFiller draftFiller;

    /**
     * 이 시간을 넘겨 DRAFT 면 멈춘 것으로 본다.
     *
     * <p>AI 읽기 타임아웃이 120초라 정상 처리는 그 안에 끝난다. 여유를 둬서 5분으로 잡는다 —
     * 짧게 잡으면 아직 응답을 기다리는 중인 계약을 다시 집어 Gemini 호출이 두 배가 된다.
     * (매칭 회차 복구와 같은 근거)
     */
    @Value("${contract.draft-recovery.stale-after-minutes:5}")
    private long staleAfterMinutes;

    @Value("${contract.draft-recovery.batch-size:20}")
    private int batchSize;

    /** 선두 한 건을 기다려 줄 시간. 넘으면 파이썬이 느린 것이니 나머지는 다음 주기로 미룬다. */
    @Value("${contract.draft-recovery.probe-timeout-seconds:10}")
    private long probeTimeoutSeconds;

    /**
     * <p><b>여기서 파이썬을 부르지 않는다.</b> {@code @Scheduled} 는
     * {@code messageBrokerTaskScheduler}(풀 1개)에서 도는데, 그건 다른 배치 다섯 개와 STOMP
     * 브로커가 함께 쓰는 스레드다. 대상만 찾아 filler 에 넘기고 바로 끝낸다.
     *
     * <p><b>선두 한 건이 실패하면 나머지는 건너뛴다.</b> DRAFT 가 쌓였다는 건 대개 파이썬이나
     * Gemini 가 죽었다는 뜻이라 20건을 다 찔러도 결과는 같고 부작용만 남는다. 그 부작용이
     * 계약 안에서 끝나지 않는 게 문제다 — 파이썬의 Gemini 키는 매칭·협상·챗봇·임베딩이 함께
     * 쓰고, 한도에 걸린 키를 다시 찌르면 쿨다운이 300초 더 밀린다. 한 번만 찔러 보고 물러난다.
     *
     * <p>건너뛴 계약은 다음 주기에 다시 집는다. 포기 시한은 생성 시각 기준이라 미뤄지지 않는다.
     */
    public void recoverStuckDrafts() {
        List<Long> stuck = contractRepository.findStuckDraftIds(
                LocalDateTime.now().minusMinutes(staleAfterMinutes), batchSize);
        if (stuck.isEmpty()) {
            return;
        }

        log.info("[계약서 문구 복구] DRAFT 로 멈춘 계약 {}건을 다시 시도한다. staleAfterMinutes={}",
                stuck.size(), staleAfterMinutes);

        Long head = stuck.get(0);
        if (!probe(head)) {
            log.warn("[계약서 문구 복구] 선두 계약 {} 이 실패했다. 남은 {}건은 다음 주기로 미룬다.",
                    head, stuck.size() - 1);
            return;
        }
        stuck.stream().skip(1).forEach(draftFiller::fill);
    }

    /** 선두 한 건. 스케줄러 스레드를 오래 잡지 않도록 짧게만 기다린다. */
    private boolean probe(Long contractId) {
        try {
            return draftFiller.probe(contractId).get(probeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            // 타임아웃 포함. 살았는지 죽었는지 모르는 상태라 나머지를 보내지 않는다.
            log.warn("[계약서 문구 복구] 선두 계약 {} 판정 실패. 남은 건은 다음 주기로 미룬다. cause={}",
                    contractId, e.toString());
            return false;
        }
    }
}
