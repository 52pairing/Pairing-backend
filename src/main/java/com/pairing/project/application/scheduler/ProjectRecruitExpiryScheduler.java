package com.pairing.project.application.scheduler;

import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모집 기간 만료 처리. (정책 P46)
 *
 * <p>모집 기간은 기본 2주이고 1주씩 최대 2회 연장할 수 있다. 마감이 지났는데 아직 모집 중이면
 * 필요한 인원이 확정되지 않은 것이다. 인원이 다 찼다면 계약 도메인이 이미 진행중으로 넘겼기 때문이다. (P47)
 *
 * <p>연장하지 않고 기본 2주가 지난 경우도 만료 대상이다. 다만 파기 판정은 아니라 위약금이 없다.
 * 연장을 다 쓰고도 만료된 경우만 클라이언트 파기로 보고 위약금을 매긴다.
 *
 * <p>위약금 생성은 아직 없다. 산정 기준이 계약 금액인데 계약 도메인이 비어 있다.
 * 지금은 대상 여부만 로그로 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRecruitExpiryScheduler {

    /** 취소된 프로젝트를 보관하는 기간. (정책 P52) */
    private static final int RETENTION_YEARS = 5;

    private final ProjectRepository projectRepository;

    /**
     * 마감이 지난 모집을 닫는다. 기본은 매일 00:10.
     *
     * <p>한 건이 실패해도 나머지는 처리한다. 프로젝트마다 트랜잭션을 나누지 않아
     * 예외가 나면 그 건만 건너뛰고 다음으로 넘어간다.
     */
    @Scheduled(cron = "${project.recruit-expiry.cron:0 10 0 * * *}")
    @Transactional
    public void expireOverdueRecruiting() {
        List<Project> expired = projectRepository.findExpiredRecruiting(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }

        LocalDate retentionUntil = LocalDate.now().plusYears(RETENTION_YEARS);
        int closed = 0;

        for (Project project : expired) {
            try {
                boolean penaltyTarget = project.isExtensionExhausted();

                project.expireRecruit(retentionUntil);
                projectRepository.updateStateWithPositions(project);
                closed++;

                if (penaltyTarget) {
                    // TODO: 계약 도메인이 붙으면 클라이언트 파기 위약금을 생성한다. (P31 · P46)
                    log.warn("[모집 만료] 프로젝트 {} — 연장 소진 후 미확정. 파기 판정 대상", project.getId());
                } else {
                    log.info("[모집 만료] 프로젝트 {} — 기간 경과로 취소. 위약금 없음", project.getId());
                }

            } catch (Exception e) {
                log.error("[모집 만료] 프로젝트 {} 처리 실패", project.getId(), e);
            }
        }

        log.info("[모집 만료] 대상 {}건 중 {}건 처리 완료", expired.size(), closed);
    }
}
