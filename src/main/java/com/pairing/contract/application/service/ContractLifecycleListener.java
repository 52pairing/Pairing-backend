package com.pairing.contract.application.service;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.project.application.event.ProjectClosedEvent;
import com.pairing.project.application.event.ProjectCompletionRequestedEvent;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

/**
 * 체결 이후 계약 상태를 프로젝트에 맞춘다.
 *
 * <p>계약관리 화면의 "진행 중 · 정산 대기 · 완료" 탭은 계약 하나로 걸러야 하는데, 그 단계는
 * 프로젝트 단위로 움직인다. 그래서 프로젝트가 내는 신호를 받아 계약을 따라 옮긴다.
 *
 * <p><b>같은 트랜잭션에서 돈다.</b> 프로젝트가 완료 처리되면서 계약만 이전 상태로 남으면 화면이
 * 어긋나므로, 실패하면 프로젝트 전이까지 함께 되돌리는 편이 낫다. 상태 한 칸을 옮기는 일이라
 * 무거운 작업도 없다. (알림처럼 실패해도 되는 부수 효과는 여기 넣지 않는다.)
 *
 * <p>이벤트는 프로젝트의 <b>모든</b> 계약에 적용된다. 파기·거부된 계약은 각 전이 메서드가
 * 스스로 걸러낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractLifecycleListener {

    private final ContractRepository contractRepository;

    /** 전원 계약 + 전원 착수금 결제 완료. 프로젝트가 진행중이 됐다. (P47) */
    @EventListener
    @Transactional
    public void on(ProjectProgressStartedEvent event) {
        advance(event.projectId(), "진행중", Contract::startProgress);
    }

    /** 클라이언트가 완료 처리했다. 성공보수 결제가 남아 정산 대기다. (P32) */
    @EventListener
    @Transactional
    public void on(ProjectCompletionRequestedEvent event) {
        advance(event.projectId(), "정산 대기", Contract::requestCompletion);
    }

    /** 성공보수 결제까지 끝났다. 리뷰는 이 시점부터 열린다. (P30·P51) */
    @EventListener
    @Transactional
    public void on(ProjectClosedEvent event) {
        advance(event.projectId(), "종료", Contract::complete);
    }

    /**
     * 프로젝트의 계약을 한 단계 옮긴다.
     *
     * <p>바뀐 것만 저장한다. 전이 메서드가 false 를 돌려주면 대상이 아니라는 뜻이라
     * (이미 그 단계이거나, 파기됐거나) 건드리지 않는다.
     */
    private void advance(Long projectId, String label, Predicate<Contract> transition) {
        List<Contract> moved = contractRepository.findByProjectId(projectId).stream()
                .filter(transition)
                .toList();

        moved.forEach(contractRepository::updateState);

        if (!moved.isEmpty()) {
            log.info("계약 상태를 {} 로 옮겼다. projectId={}, 건수={}", label, projectId, moved.size());
        }
    }
}
