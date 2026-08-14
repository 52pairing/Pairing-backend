package com.pairing.matching.application.service;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오래 {@code RUNNING}으로 멈춰 있는 추천 회차를 되살린다.
 *
 * <p><b>왜 필요한가.</b> 후보 채우기는 AI 호출이라 수 초~수십 초가 걸리는데, 그 사이 컨테이너가
 * 교체되면 스레드가 아무 흔적 없이 사라진다. 회차는 {@code RUNNING}인 채로 영원히 남고 화면은
 * "추천 준비중"에서 멈춘다. 2026-08-13에 하루 동안 ECS 태스크 정의가 네 번 바뀌었다 — 드문 일이
 * 아니라 배포할 때마다 생길 수 있는 일이다.
 *
 * <p><b>왜 다시 채워도 안전한가.</b> 후보 저장과 회차 완료가 한 트랜잭션이라
 * ({@link MatchingRoundFiller#fill}) 중간에 죽으면 통째로 롤백된다. 그래서 멈춘 회차에는 후보가
 * 하나도 없고, 다시 채워도 중복이 생기지 않는다. 반대로 후보가 저장됐다면 회차는 이미
 * {@code COMPLETED}라 여기 걸리지 않는다.
 *
 * <p><b>왜 실패로 닫지 않고 재시도하나.</b> 최초 추천이 실패하면 클라이언트에게 복구 수단이 없다 —
 * 무료 재추천은 "보낸 요청이 전원 거절"일 때만 열리고(P41), 유료 재추천은 돈을 낸다. 착수금을 이미
 * 낸 클라이언트에게 다시 결제를 요구할 수는 없으므로 서버가 알아서 되살린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaleRoundRecoveryService {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRoundFiller matchingRoundFiller;

    /**
     * 이 시간을 넘겨 RUNNING이면 멈춘 것으로 본다.
     *
     * <p>AI 호출의 읽기 타임아웃이 60초라 정상 처리는 아무리 늦어도 그 안에 끝난다. 여유를 둬서
     * 5분으로 잡는다 — 짧게 잡으면 정상 진행 중인 회차를 다시 채워 Gemini 비용을 두 배로 쓴다.
     */
    @Value("${matching.round-recovery.stale-after-minutes:5}")
    private long staleAfterMinutes;

    public void recoverStaleRounds() {
        List<MatchingRound> stale = matchingRoundRepository
                .findStaleRunning(LocalDateTime.now().minusMinutes(staleAfterMinutes));
        if (stale.isEmpty()) {
            return;
        }
        log.info("MATCHING_DEBUG java.round.recovery.start staleCount={} staleAfterMinutes={}",
                stale.size(), staleAfterMinutes);

        int recovered = 0;
        int failed = 0;
        for (MatchingRound round : stale) {
            try {
                matchingRoundFiller.fill(round.getId());
                recovered++;
                log.info("MATCHING_DEBUG java.round.recovery.filled roundId={} positionId={}",
                        round.getId(), round.getPositionId());
            } catch (Exception e) {
                // 두 번째도 실패하면 FAILED로 닫는다. RUNNING으로 두면 다음 주기에 또 집어서
                // AI 서버가 죽어 있는 동안 계속 호출한다.
                failed++;
                log.error("MATCHING_DEBUG java.round.recovery.failed roundId={} positionId={}",
                        round.getId(), round.getPositionId(), e);
                matchingRoundFiller.markFailed(round.getId());
            }
        }
        log.info("MATCHING_DEBUG java.round.recovery.summary staleCount={} recovered={} failed={}",
                stale.size(), recovered, failed);
    }
}
