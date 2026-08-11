package com.pairing.contract.application.service;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.event.ProjectClosedEvent;
import com.pairing.project.application.event.ProjectCompletionRequestedEvent;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 체결 이후 계약이 프로젝트를 따라가는지. 계약관리 화면의 "진행 중 · 정산 대기 · 완료" 탭이
 * 이 전이에 걸려 있다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractLifecycleListenerTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long CLIENT_ACCOUNT_ID = 1000L;
    private static final Long FREELANCER_ACCOUNT_ID = 2000L;

    @Mock
    private ContractRepository contractRepository;

    private ContractLifecycleListener listener;
    private Contract contract;

    @BeforeEach
    void setUp() {
        listener = new ContractLifecycleListener(contractRepository);
        contract = signedContract();
        given(contractRepository.findByProjectId(PROJECT_ID)).willReturn(List.of(contract));
    }

    private Contract signedContract() {
        Contract created = Contract.create(300L, PROJECT_ID, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        created.completeDraft("{}", null);
        created.sign(CLIENT_ACCOUNT_ID, "SESSION", null, null, null, null);
        created.sign(FREELANCER_ACCOUNT_ID, "SESSION", null, null, null, null);
        return created;
    }

    @Test
    @DisplayName("전원 착수금 결제로 프로젝트가 진행중이 되면 계약도 진행중이 된다")
    void movesToInProgress() {
        listener.on(new ProjectProgressStartedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.IN_PROGRESS);
        verify(contractRepository).updateState(contract);
    }

    @Test
    @DisplayName("클라이언트가 완료 처리하면 정산 대기가 된다")
    void movesToCompletionPending() {
        contract.startProgress();

        listener.on(new ProjectCompletionRequestedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.COMPLETION_PENDING);
    }

    @Test
    @DisplayName("착수금 미납으로 체결 상태에 머문 계약도 완료 처리에 함께 넘어간다")
    void movesToCompletionPendingFromSigned() {
        // 일이 끝났다는 사실은 프로젝트가 안다. 미납은 정산이 따로 쫓는다.
        listener.on(new ProjectCompletionRequestedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.COMPLETION_PENDING);
    }

    @Test
    @DisplayName("성공보수 결제까지 끝나면 종료된다")
    void movesToCompleted() {
        contract.startProgress();
        contract.requestCompletion();

        listener.on(new ProjectClosedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.COMPLETED);
        assertThat(contract.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("서명이 안 끝난 계약은 진행중으로 넘어가지 않는다")
    void skipsUnsignedContract() {
        Contract pending = Contract.create(301L, PROJECT_ID, 11L, 100L, 201L,
                CLIENT_ACCOUNT_ID, 2001L, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        pending.completeDraft("{}", null);
        given(contractRepository.findByProjectId(PROJECT_ID)).willReturn(List.of(pending));

        listener.on(new ProjectProgressStartedEvent(PROJECT_ID));

        assertThat(pending.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        verify(contractRepository, never()).updateState(any());
    }

    @Test
    @DisplayName("중도 파기된 계약은 되살아나지 않는다")
    void skipsTerminatedContract() {
        // 이벤트는 프로젝트의 모든 계약에 온다. 파기된 사람까지 진행중으로 되돌리면 안 된다.
        contract.terminate(PartyRole.FREELANCER, LocalDate.of(2031, 12, 31));

        listener.on(new ProjectProgressStartedEvent(PROJECT_ID));
        listener.on(new ProjectCompletionRequestedEvent(PROJECT_ID));
        listener.on(new ProjectClosedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        verify(contractRepository, never()).updateState(any());
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 와도 한 번만 옮긴다")
    void isIdempotent() {
        listener.on(new ProjectProgressStartedEvent(PROJECT_ID));
        listener.on(new ProjectProgressStartedEvent(PROJECT_ID));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.IN_PROGRESS);
        verify(contractRepository).updateState(contract);
    }
}
