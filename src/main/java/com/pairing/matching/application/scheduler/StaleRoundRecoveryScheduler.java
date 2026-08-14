package com.pairing.matching.application.scheduler;

import com.pairing.matching.application.service.StaleRoundRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 추천 회차가 {@code RUNNING}으로 멈춰 있으면 되살린다.
 *
 * <p>후보 채우기(AI 호출) 도중 컨테이너가 교체되면 그 스레드는 아무 흔적 없이 사라지고, 회차는
 * {@code RUNNING}인 채로 남아 클라이언트 화면이 "추천 준비중"에서 멈춘다. 서버가 알아서 다시
 * 채워주지 않으면 클라이언트에게는 복구 수단이 없다 — 무료 재추천은 조건이 다르고 유료는 돈을 낸다.
 *
 * <p>스케줄링 활성화({@code @EnableScheduling})는 {@code ProjectSchedulingConfig}가 전역으로 켜둔다.
 * 실제 로직은 {@link StaleRoundRecoveryService}에 있다 — 여기는 얇은 진입점.
 */
@Component
@RequiredArgsConstructor
public class StaleRoundRecoveryScheduler {

    private final StaleRoundRecoveryService staleRoundRecoveryService;

    /**
     * 기본은 5분마다. 클라이언트가 결제 직후 화면을 보고 있는 상황이라 만료 처리(10분)보다 자주 돈다.
     *
     * <p>배포로 죽은 회차를 되살리는 게 목적이므로 <b>배포 직후에 한 번은 돌아야</b> 한다.
     */
    @Scheduled(cron = "${matching.round-recovery.cron:0 */5 * * * *}")
    public void recoverStaleRounds() {
        staleRoundRecoveryService.recoverStaleRounds();
    }
}
