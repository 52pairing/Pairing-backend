package com.pairing.contract.application.service;

import com.pairing.contract.application.port.FreelancerGradeReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.application.command.CreateFreelancerSuccessFeeCommand;
import com.pairing.settlement.application.usecase.SuccessFeeSettlementUseCase;
import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.application.event.ProjectClosedEvent;
import com.pairing.project.application.event.ProjectCompletionRequestedEvent;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

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

    /** 파기된 계약을 보관하는 기간. (정책 P52) */
    private static final int RETENTION_YEARS = 5;

    private final ContractRepository contractRepository;
    private final FreelancerGradeReaderPort freelancerGradeReaderPort;
    private final SuccessFeeSettlementUseCase successFeeSettlementUseCase;

    /** 전원 계약 + 전원 착수금 결제 완료. 프로젝트가 진행중이 됐다. (P47) */
    @EventListener
    @Transactional
    public void on(ProjectProgressStartedEvent event) {
        advance(event.projectId(), "진행중", Contract::startProgress);
    }

    /**
     * 클라이언트가 완료 처리했다. 성공보수 결제가 남아 정산 대기다. (P32)
     *
     * <p>이 시점에 <b>프리랜서 성공보수 수수료</b>도 함께 만든다(P30). 클라이언트 분은 프로젝트가
     * 직접 만들지만, 프리랜서 분은 누가 프리랜서인지 계약만 알기 때문에 여기서 만든다. 프로젝트가
     * 계약을 읽게 하면 {@code project ↔ contract} 참조 순환이 생긴다.
     */
    @EventListener
    @Transactional
    public void on(ProjectCompletionRequestedEvent event) {
        advance(event.projectId(), "정산 대기", Contract::requestCompletion);
        createFreelancerSuccessFees(event.projectId());
    }

    /**
     * 그 프로젝트에서 일한 프리랜서마다 성공보수 정산을 만든다.
     *
     * <p>기준 금액은 <b>각자의 계약 총액</b>이다. 착수금과 같다. 프로젝트 예산으로는 여러 명의
     * 몫을 나눌 수 없다.
     *
     * <p>파기·거부된 계약은 제외한다. 일을 하지 않은 사람에게 성공보수를 청구할 수 없다.
     * 계약 1건당 1건이라 완료 처리가 재시도돼도 두 번 청구되지 않는다(정산 쪽에서 막는다).
     */
    private void createFreelancerSuccessFees(Long projectId) {
        contractRepository.findByProjectId(projectId).stream()
                .filter(contract -> contract.getStatus() == ContractStatus.COMPLETION_PENDING)
                .forEach(contract -> successFeeSettlementUseCase.createFreelancerSuccessFee(
                        new CreateFreelancerSuccessFeeCommand(
                                projectId,
                                contract.getId(),
                                contract.accountIdOf(PartyRole.FREELANCER),
                                contract.getTotalAmount(),
                                freelancerGradeReaderPort.findGrade(contract.getFreelancerId()))));
    }

    /** 성공보수 결제까지 끝났다. 리뷰는 이 시점부터 열린다. (P30·P51) */
    @EventListener
    @Transactional
    public void on(ProjectClosedEvent event) {
        advance(event.projectId(), "종료", Contract::complete);
    }

    /**
     * 프로젝트가 취소됐다. 걸려 있던 계약을 중도 파기로 옮긴다. (P46)
     *
     * <p>{@link ProjectClosedEvent}(정상 종료)와 반대 방향이다. 그쪽은 완료로 올리고 이쪽은
     * 되돌린다. 둘을 한 이벤트로 묶으면 취소된 프로젝트의 계약이 완료 처리된다.
     *
     * <p>체결 전 계약도 대상이다. 서명 대기로 남겨두면 프리랜서가 서명을 눌렀을 때
     * 프로젝트 쪽에서 {@code PJ_012} 가 올라와 계약 화면에 남의 도메인 에러가 뜬다.
     *
     * <p>독립 트랜잭션으로 돈다. 다른 리스너와 달리 <b>여기서 실패해도 프로젝트 취소는
     * 유지돼야 한다.</b> 취소를 되돌리면 스케줄러 로그에는 처리됐다고 남는데 실제로는
     * 아무것도 안 바뀐 상태가 되어 원인을 찾기 어렵다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProjectCanceledEvent event) {
        LocalDate retentionUntil = LocalDate.now().plusYears(RETENTION_YEARS);
        advance(event.projectId(), "중도 파기", contract -> contract.cancelByProject(retentionUntil));
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
