package com.pairing.contract.application.scheduler;

import com.pairing.contract.application.service.ContractDraftRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DRAFT 로 멈춘 계약서를 되살린다.
 *
 * <p>스케줄링 활성화({@code @EnableScheduling})는 {@code ProjectSchedulingConfig} 가 전역으로
 * 켜둔다. 실제 로직은 {@link ContractDraftRecoveryService} 에 있다 — 여기는 얇은 진입점.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractDraftRecoveryScheduler {

    private final ContractDraftRecoveryService recoveryService;

    /**
     * 기본 5분. 파이썬의 Gemini 키 쿨다운이 300초라 그 회복 주기에 맞춘다.
     *
     * <p><b>30초에 돈다.</b> 매칭 회차 복구({@code matching.round-recovery.cron})가 같은 5분
     * 주기의 0초에 도는데, {@code @Scheduled} 는 {@code messageBrokerTaskScheduler}(풀 1개)를
     * 다른 배치들과 함께 쓴다. 같은 시각에 발화하면 둘이 직렬로 돌고 실행 순서도 빈 등록
     * 순서에 달려 보장되지 않는다. 이쪽이 탐침에서 최대 10초를 기다리므로, 겹치면 매칭 복구가
     * 그만큼 늦게 시작한다. 30초만 밀어도 그 결합이 사라진다.
     *
     * <p>예외를 여기서 삼킨다. {@code @Async} 제출이 큐 초과로 거부되면 예외가 스케줄러 밖으로
     * 나가는데, 이 스레드는 다른 배치 다섯 개가 함께 쓴다.
     */
    @Scheduled(cron = "${contract.draft-recovery.cron:30 */5 * * * *}")
    public void recoverStuckDrafts() {
        try {
            recoveryService.recoverStuckDrafts();
        } catch (Exception e) {
            log.error("[계약서 문구 복구] 배치가 실패했다. 다음 주기에 다시 시도한다.", e);
        }
    }
}
