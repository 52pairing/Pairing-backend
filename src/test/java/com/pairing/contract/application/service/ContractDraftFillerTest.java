package com.pairing.contract.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.contract.application.port.ContractDraftPort;
import com.pairing.contract.application.port.ContractProjectReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractDraftText;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * DRAFT 계약서에 본문 문구를 채워 서명 대기로 넘기는 규칙.
 *
 * <p>핵심은 <b>실패했을 때 어디에 머무느냐</b>다. 포기 시한 전이면 DRAFT 로 남아 아무도 서명하지
 * 못하고, 시한을 넘기면 원문을 잘라 넣고 계약을 연다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractDraftFillerTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long PROJECT_ID = 1L;
    private static final String AGREED_NOTE = "산출물은 매주 금요일에 공유한다";
    private static final long GIVE_UP_MINUTES = 30L;

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractProjectReaderPort projectReaderPort;
    @Mock
    private ContractDraftPort draftPort;
    @Mock
    private NotificationCreateUseCase notificationCreateUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ContractDraftFiller filler;
    private Contract contract;

    @BeforeEach
    void setUp() {
        filler = new ContractDraftFiller(contractRepository, projectReaderPort, draftPort,
                notificationCreateUseCase, objectMapper);
        ReflectionTestUtils.setField(filler, "giveUpAfterMinutes", GIVE_UP_MINUTES);

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

    private void aiFails() {
        willThrow(new BusinessException(ContractErrorCode.DRAFT_TEXT_UNAVAILABLE))
                .given(draftPort).draft(any());
    }

    @Test
    @DisplayName("문구가 채워지면 서명 대기로 넘어가고 양측에 알린다")
    void completesDraftAndNotifiesBothParties() {
        given(draftPort.draft(any())).willReturn(
                new ContractDraftText("API 설계 및 개발", "Node.js 기반 API 구현", "매주 금요일 산출물 공유"));

        filler.fill(CONTRACT_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        assertThat(contract.getContentJson()).contains("API 설계 및 개발");
        assertThat(contract.getSpecialTerms()).isEqualTo("매주 금요일 산출물 공유");
        verify(notificationCreateUseCase, times(2)).create(any());
    }

    @Test
    @DisplayName("AI 가 실패하고 포기 시한 전이면 DRAFT 에 남긴다")
    void staysDraftWhenAiFailsBeforeGiveUpTime() {
        // 덜 다듬어진 계약서가 서명 단계로 넘어가지 않게 막는 것이 이 기능의 목적이다.
        aiFails();

        filler.fill(CONTRACT_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.DRAFT);
        verify(contractRepository, never()).updateState(any());
        verify(notificationCreateUseCase, never()).create(any());
    }

    @Test
    @DisplayName("포기 시한을 넘기면 원문을 잘라 넣고 계약을 연다")
    void fallsBackToRawTextAfterGiveUpTime() {
        // 키 만료·일일 한도 소진 같은 영구 장애는 재시도로 풀리지 않는다.
        // 무기한 기다리면 계약이 영영 열리지 않으므로 여기서는 여는 쪽을 택한다.
        //
        // 시한을 0으로 낮추는 대신 계약을 실제로 늙힌다. 0이면 createdAt 과 now() 가 같은
        // 시계 틱에 잡혀 판정이 흔들린다.
        ReflectionTestUtils.setField(contract, "createdAt",
                LocalDateTime.now().minusMinutes(GIVE_UP_MINUTES + 1));
        aiFails();

        filler.fill(CONTRACT_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        assertThat(contract.getContentJson()).contains("담당 업무 원문");
    }

    @Test
    @DisplayName("합의 메모가 있는데 특약 없음이 오면 협상 원문을 지킨다")
    void keepsAgreedTermsWhenAiReturnsNoSpecialTerms() {
        // 파이썬은 합의 메모가 없을 때와 LLM 이 빈 값을 뱉었을 때 모두 같은 문구를 준다.
        // 메모가 있었다면 후자이므로, 합의 내용을 "없음"으로 지우면 안 된다.
        given(draftPort.draft(any())).willReturn(
                new ContractDraftText("API 설계", "", ContractDraftText.NO_SPECIAL_TERMS));

        filler.fill(CONTRACT_ID);

        assertThat(contract.getSpecialTerms()).isEqualTo(AGREED_NOTE);
    }

    @Test
    @DisplayName("담당 업무 원문이 없으면 AI 를 부르지 않는다")
    void skipsAiWhenMainTaskIsBlank() {
        // 파이썬 main_task 가 필수라 빈 값으로 부르면 422 로 튕긴다. 왕복을 낭비하지 않는다.
        given(projectReaderPort.findForContract(PROJECT_ID)).willReturn(project(null, "세부 범위 원문"));

        filler.fill(CONTRACT_ID);

        verify(draftPort, never()).draft(any());
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
    }

    @Test
    @DisplayName("알림이 실패해도 계약은 서명 대기로 넘어간다")
    void notificationFailureDoesNotBlockSigning() {
        // 알림은 부수 효과다. 여기서 예외가 나가면 같은 트랜잭션인 상태 전이까지 롤백되어
        // 계약이 DRAFT 에 갇히고 아무도 서명하지 못한다.
        given(draftPort.draft(any())).willReturn(new ContractDraftText("API 설계", "구현", "특약"));
        given(notificationCreateUseCase.create(any()))
                .willThrow(new RuntimeException("알림 저장 실패"));

        filler.fill(CONTRACT_ID);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        verify(contractRepository).updateState(any());
    }

    @Test
    @DisplayName("이미 서명 대기면 두 번 처리하지 않는다")
    void isIdempotent() {
        // 리스너와 복구 스케줄러가 같은 계약에 겹쳐 들어올 수 있다.
        given(draftPort.draft(any())).willReturn(new ContractDraftText("API 설계", "구현", "특약"));

        filler.fill(CONTRACT_ID);
        filler.fill(CONTRACT_ID);

        verify(contractRepository, times(1)).updateState(any());
        verify(notificationCreateUseCase, times(2)).create(any());
    }

    @Test
    @DisplayName("탐침은 AI 실패를 false 로 알려 배치를 멈추게 한다")
    void probeReportsAiFailure() throws Exception {
        aiFails();

        assertThat(filler.probe(CONTRACT_ID).get()).isFalse();
    }

    @Test
    @DisplayName("탐침은 AI 성공을 true 로 알려 배치를 잇게 한다")
    void probeReportsAiSuccess() throws Exception {
        given(draftPort.draft(any())).willReturn(new ContractDraftText("API 설계", "구현", "특약"));

        assertThat(filler.probe(CONTRACT_ID).get()).isTrue();
    }
}
