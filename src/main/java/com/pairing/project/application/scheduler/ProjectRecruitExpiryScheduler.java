package com.pairing.project.application.scheduler;

import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모집 기간 만료 처리. (정책 P46)
 *
 * <p>모집 기간은 기본 2주이고 1주씩 최대 2회 연장할 수 있다(최대 4주). 마감이 지났는데
 * 필요한 인원이 확정되지 않았으면 프로젝트를 취소한다.
 *
 * <p><b>판정 기준은 상태가 아니라 인원이다.</b> 프리랜서가 한 명이라도 수락하면 프로젝트가
 * 협상중으로 넘어가는데, 상태로 거르면 그 프로젝트는 마감이 지나도 영원히 잡히지 않는다.
 * 정책이 말하는 기준도 "필요한 인원이 확정되지 않은 경우" 다.
 *
 * <p>연장하지 않고 기본 2주가 지난 경우도 만료 대상이다. 연장을 다 쓰고도 미확정인 경우만
 * 클라이언트 파기로 본다. 위약금은 산정 기준(P28 제12조 수행분)이 미정이라 판정만 남긴다.
 *
 * <p><b>트랜잭션을 여기 걸지 않는다.</b> 걸면 한 건의 실패가 배치 전체를 되돌린다.
 * 건별 격리는 {@link ProjectExpirer} 가 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRecruitExpiryScheduler {

    private final ProjectRepository projectRepository;
    private final ProjectExpirer projectExpirer;

    /**
     * 마감이 지난 모집을 닫는다. 기본은 매일 00:10.
     *
     * <p>{@code project.recruit-expiry.cron} 을 {@code "-"} 로 두면 꺼진다. 다른 도메인의
     * 정리 리스너가 아직 안 붙었을 때 배포를 막지 않고 기능만 잠글 수 있다.
     */
    @Scheduled(cron = "${project.recruit-expiry.cron:0 10 0 * * *}")
    public void expireOverdueRecruiting() {
        List<Project> expired = projectRepository.findExpiredUnderstaffed(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }

        int closed = 0;
        int penaltyTargets = 0;

        for (Project project : expired) {
            try {
                if (projectExpirer.expireOne(project.getId())) {
                    penaltyTargets++;
                    // TODO: 위약금 산정 기준이 정해지면 클라이언트 파기 위약금을 만든다. (P31 · P46)
                    log.warn("[모집 만료] 프로젝트 {} — 연장 소진 후 미확정. 파기 판정 대상", project.getId());
                }
                closed++;

            } catch (Exception e) {
                // 건별 독립 트랜잭션이라 이 건만 남고 나머지는 계속 처리된다.
                log.error("[모집 만료] 프로젝트 {} 처리 실패", project.getId(), e);
            }
        }

        log.info("[모집 만료] 대상 {}건 중 {}건 처리 완료. 파기 판정 {}건",
                expired.size(), closed, penaltyTargets);
    }
}
