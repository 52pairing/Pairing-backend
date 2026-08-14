package com.pairing.project.application.scheduler;

import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import com.pairing.project.exception.ProjectErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 프로젝트 1건을 독립 트랜잭션으로 만료 처리한다. (정책 P46)
 *
 * <p><b>왜 나눴나.</b> 예전에는 스케줄러 메서드 하나에 {@code @Transactional} 을 걸고 반복문
 * 안에서 건별로 예외를 잡았다. 그러면 한 건에서 JPA 예외가 나는 순간 그 트랜잭션이
 * rollback-only 로 마킹되고, {@code catch} 로 삼켜도 마킹은 지워지지 않아 커밋 시점에
 * {@code UnexpectedRollbackException} 이 터진다. 로그에는 "10건 중 9건 처리 완료" 가 찍히는데
 * 실제로는 0건이 되는 상황이다. 매칭 요청 만료도 같은 이유로 분리해 두었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectExpirer {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 한 건을 취소하고 정리 신호를 낸다.
     *
     * <p>스케줄러가 조회한 인스턴스를 그대로 쓰지 않고 <b>다시 읽는다.</b> 조회 시점과 처리 시점
     * 사이에 서명이 끝나 인원이 찼을 수 있다. 그 사이에 확정된 프로젝트를 취소하면 되돌릴 수 없다.
     *
     * <p>{@code ProjectCanceledEvent} 는 이 트랜잭션 안에서 발행한다. 받는 쪽이
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 을 쓰면 트랜잭션이 없을 때 아예 뜨지
     * 않는데, 예외도 안 나고 조용히 넘어가서 원인을 찾기 어렵다.
     *
     * @return 파기 판정 대상이면 true. 연장을 다 쓰고도 미확정인 경우다 (P46)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean expireOne(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // 저장된 확정 인원이 포지션 합계와 어긋나면 판정 근거를 믿을 수 없다. 아무것도 하지 않는다.
        if (!project.hasConsistentHeadcount()) {
            log.error("[모집 만료] 확정 인원이 포지션 합계와 다르다. 건너뛴다. projectId={}", projectId);
            return false;
        }

        boolean penaltyTarget = project.isExtensionExhausted();

        project.expireRecruit(LocalDate.now().plusYears(RETENTION_YEARS));
        projectRepository.updateStateWithPositions(project);

        // 요청·협상·계약·정산 정리는 받는 쪽이 각자 한다.
        eventPublisher.publishEvent(new ProjectCanceledEvent(projectId));

        return penaltyTarget;
    }

    /** 취소된 프로젝트를 보관하는 기간. (정책 P52) */
    private static final int RETENTION_YEARS = 5;
}
