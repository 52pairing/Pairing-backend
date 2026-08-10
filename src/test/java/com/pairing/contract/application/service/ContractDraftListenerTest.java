package com.pairing.contract.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.event.ContractCreatedEvent;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** DRAFT 계약서에 본문 문구를 채워 서명 대기로 넘기는 규칙. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractDraftListenerTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long PROJECT_ID = 1L;
    private static final String AGREED_NOTE = "산출물은 매주 금요일에 공유한다";

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractProjectReaderPort projectReaderPort;
    @Mock
    private ContractDraftPort draftPort;
    @Mock
    private NotificationCreateUseCase notificationCreateUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContractDraftListener listener;
    private Contract contract;

    @BeforeEach
    void setUp() {
        listener = new ContractDraftListener(contractRepository, projectReaderPort, draftPort,
                notificationCreateUseCase, objectMapper);

        contract = Contract.create(300L, PROJECT_ID, 10L, 100L, 200L, 1000L, 2000L,
                5_000_000L, 4, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, AGREED_NOTE);

        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(projectReaderPort.findForContract(PROJECT_ID)).willReturn(project("담당 업무 원문", "세부 범위 원문"));
    }

    private ContractProjectReaderPort.ProjectContractView project(String mainTask, String detailScope) {
        return new ContractProjectReaderPort.ProjectContractView(
                100L, WorkStyle.REMOTE, WorkForm.FULL_TIME, null,
                LocalDate.of(2026, 9, 1), 4, PeriodUnit.MONTH, mainTask, detailScope);
    }

    private ContractCreatedEvent event(List<String> agreedNotes) {
        return new ContractCreatedEvent(CONTRACT_ID, PROJECT_ID, agreedNotes);
    }

    @Test
    @DisplayName("문구가 채워지면 서명 대기로 넘어가고 양측에 알린다")
    void completesDraftAndNotifiesBothParties() {
        given(draftPort.draft(any())).willReturn(Optional.of(
                new ContractDraftText("API 설계 및 개발", "Node.js 기반 API 구현", "매주 금요일 산출물 공유")));

        listener.on(event(List.of(AGREED_NOTE)));

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        assertThat(contract.getContentJson()).contains("API 설계 및 개발");
        assertThat(contract.getSpecialTerms()).isEqualTo("매주 금요일 산출물 공유");
        verify(notificationCreateUseCase, times(2)).create(any());
    }

    @Test
    @DisplayName("AI 가 실패해도 원문을 잘라 넣고 서명 대기로 넘어간다")
    void fallsBackToRawTextWhenAiFails() {
        given(draftPort.draft(any())).willReturn(Optional.empty());

        listener.on(event(List.of(AGREED_NOTE)));

        // 서명 가능한 계약서에는 항상 업무 내용이 들어 있어야 한다.
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        assertThat(contract.getContentJson()).contains("담당 업무 원문");
    }

    @Test
    @DisplayName("합의 메모가 있는데 특약 없음이 오면 협상 원문을 지킨다")
    void keepsAgreedTermsWhenAiReturnsNoSpecialTerms() {
        // 파이썬은 합의 메모가 없을 때와 LLM 이 빈 값을 뱉었을 때 모두 같은 문구를 준다.
        // 메모가 있었다면 후자이므로, 합의 내용을 "없음"으로 지우면 안 된다.
        given(draftPort.draft(any())).willReturn(Optional.of(
                new ContractDraftText("API 설계", "", ContractDraftText.NO_SPECIAL_TERMS)));

        listener.on(event(List.of(AGREED_NOTE)));

        assertThat(contract.getSpecialTerms()).isEqualTo(AGREED_NOTE);
    }

    @Test
    @DisplayName("담당 업무 원문이 없으면 AI 를 부르지 않는다")
    void skipsAiWhenMainTaskIsBlank() {
        // 파이썬 main_task 가 필수라 빈 값으로 부르면 422 로 튕긴다. 왕복을 낭비하지 않는다.
        given(projectReaderPort.findForContract(PROJECT_ID)).willReturn(project(null, "세부 범위 원문"));

        listener.on(event(List.of(AGREED_NOTE)));

        verify(draftPort, never()).draft(any());
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
    }

    @Test
    @DisplayName("이미 서명 대기면 두 번 처리하지 않는다")
    void isIdempotent() {
        given(draftPort.draft(any())).willReturn(Optional.of(
                new ContractDraftText("API 설계", "구현", "특약")));

        listener.on(event(List.of(AGREED_NOTE)));
        listener.on(event(List.of(AGREED_NOTE)));

        // 두 번째 호출은 상태 전이도 알림도 하지 않는다.
        verify(contractRepository, times(1)).updateState(any());
        verify(notificationCreateUseCase, times(2)).create(any());
    }
}
