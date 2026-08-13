package com.pairing.grade.application.scheduler;

import com.pairing.grade.application.usecase.GradeCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 월간 등급 산정. (정책 P01 — "한 달마다 체크")
 *
 * <p>이게 없으면 등급은 가입 시 값에서 영원히 멈춘다. 마이페이지는 "매월 1일 자동 산정"이라고
 * 안내하고 승급 조건까지 정확히 알려주는데, 조건을 채워도 등급이 오르지 않는 상태였다.
 *
 * <p>등급에 걸린 혜택은 다른 도메인이 쓴다 — 매칭 가중치(실버 0% / 골드 1% / 다이아 2%),
 * 수수료 인하(다이아·마스터 각 1%), 프로젝트 등록 개수 제한. <b>이 배치가 그 전제다.</b>
 *
 * <p>스케줄링 활성화({@code @EnableScheduling})는 {@code ProjectSchedulingConfig} 가 전역으로 켜둔다.
 *
 * <p>인스턴스를 여러 대로 늘리면 같은 작업이 중복 실행된다. 산정은 여러 번 돌아도 결과가 같아서
 * (같은 실적으로 같은 등급이 나온다) 지금은 문제가 되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GradeRecalculationScheduler {

    private final GradeCommandUseCase gradeCommandUseCase;

    /**
     * 기본은 매월 1일 새벽 3시.
     *
     * <p>개인정보 파기(4시)와 시간을 겹치지 않게 뒀다. 둘 다 전 회원을 훑어서 같은 시각에 돌면
     * 커넥션을 서로 뺏는다.
     */
    @Scheduled(cron = "${grade.recalculation.cron:0 0 3 1 * *}")
    public void recalculateAll() {
        gradeCommandUseCase.recalculateAll();
    }
}
