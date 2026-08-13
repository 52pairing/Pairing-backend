package com.pairing.contract.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.port.ContractFileReaderPort;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.contract.application.port.FreelancerGradeReaderPort;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractSignature;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 서명 처리. 서명 그림은 선택이고, 넣었다면 실재해야 한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractSignatureTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long CLIENT_ACCOUNT_ID = 1000L;
    private static final Long FREELANCER_ACCOUNT_ID = 2000L;
    private static final Long SIGNATURE_FILE_ID = 123L;

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractFileReaderPort contractFileReaderPort;
    @Mock
    private ProjectCommandUseCase projectCommandUseCase;
    @Mock
    private ContractPartyReaderPort partyReaderPort;
    @Mock
    private DepositSettlementUseCase depositSettlementUseCase;
    @Mock
    private FreelancerGradeReaderPort freelancerGradeReaderPort;
    @Mock
    private NotificationCreateUseCase notificationCreateUseCase;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ContractCommandService service;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new ContractCommandService(contractRepository, contractFileReaderPort,
                projectCommandUseCase, partyReaderPort, depositSettlementUseCase,
                freelancerGradeReaderPort, notificationCreateUseCase, eventPublisher);

        contract = Contract.create(300L, 1L, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);   // 서명은 SIGN_PENDING 에서만 된다

        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(contractFileReaderPort.exists(SIGNATURE_FILE_ID)).willReturn(true);
        given(freelancerGradeReaderPort.findGrade(any())).willReturn(FreelancerGrade.JUNIOR);
    }

    private SignContractCommand command(Long accountId, Long signatureFileId) {
        return new SignContractCommand(CONTRACT_ID, accountId, signatureFileId, "127.0.0.1", "JUnit");
    }

    private ContractSignature signatureOf(PartyRole role) {
        return contract.getSignatures().stream()
                .filter(s -> s.getPartyRole() == role)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("서명 그림 없이 동의만으로도 서명된다")
    void signsWithoutImage() {
        // 화면의 서명 패드가 붙기 전에도 흐름이 막히지 않아야 한다.
        boolean concluded = service.sign(command(CLIENT_ACCOUNT_ID, null));

        assertThat(concluded).isFalse();
        assertThat(signatureOf(PartyRole.CLIENT).getStatus()).isEqualTo(SignatureStatus.SIGNED);
        assertThat(signatureOf(PartyRole.CLIENT).getSignatureFileId()).isNull();
        verify(contractFileReaderPort, never()).exists(any());
    }

    @Test
    @DisplayName("서명 그림을 넣으면 함께 저장된다")
    void storesSignatureImage() {
        service.sign(command(CLIENT_ACCOUNT_ID, SIGNATURE_FILE_ID));

        assertThat(signatureOf(PartyRole.CLIENT).getSignatureFileId()).isEqualTo(SIGNATURE_FILE_ID);
    }

    @Test
    @DisplayName("없는 파일을 가리키면 서명이 막힌다")
    void rejectsMissingSignatureFile() {
        // 없는 fileId 로 체결되면 나중에 PDF 를 그릴 때 서명란이 빈다. 그때는 되돌릴 수 없다.
        given(contractFileReaderPort.exists(999L)).willReturn(false);

        assertThatThrownBy(() -> service.sign(command(CLIENT_ACCOUNT_ID, 999L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContractErrorCode.SIGNATURE_NOT_FOUND);

        assertThat(signatureOf(PartyRole.CLIENT).getStatus()).isEqualTo(SignatureStatus.PENDING);
    }

    @Test
    @DisplayName("한쪽만 서명하면 상대에게만 알린다")
    void notifiesCounterpartOnly() {
        // 남은 사람이 서명해야 계약이 성립한다. 부르는 게 목적이라 서명한 본인에게는 안 보낸다.
        service.sign(command(CLIENT_ACCOUNT_ID, null));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationCreateUseCase).create(captor.capture());

        assertThat(captor.getValue().ownerAccountId()).isEqualTo(FREELANCER_ACCOUNT_ID);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.CONTRACT_SIGNED);
    }

    @Test
    @DisplayName("체결되면 양쪽에 알린다")
    void notifiesBothOnConclusion() {
        service.sign(command(CLIENT_ACCOUNT_ID, null));
        service.sign(command(FREELANCER_ACCOUNT_ID, null));

        // 1건(상대 호출) + 2건(체결 통보) = 3건
        verify(notificationCreateUseCase, times(3)).create(any());
    }

    @Test
    @DisplayName("체결되면 그 시점 정산 계좌가 계약에 굳는다")
    void freezesSettlementAccountOnConclusion() {
        // 굳혀두지 않으면 프리랜서가 나중에 계좌를 바꿨을 때 이미 체결된 계약서까지 따라 바뀐다.
        byte[] snapshot = "카카오뱅크 3333012345678 (예금주: 김민준)".getBytes(StandardCharsets.UTF_8);
        given(partyReaderPort.settlementAccountSnapshot(contract.getFreelancerId()))
                .willReturn(Optional.of(snapshot));

        service.sign(command(CLIENT_ACCOUNT_ID, null));
        assertThat(contract.getSettlementAccountEnc()).isNull();   // 한쪽 서명만으로는 안 굳는다

        service.sign(command(FREELANCER_ACCOUNT_ID, null));
        assertThat(contract.getSettlementAccountEnc()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("계좌가 없는 프리랜서여도 체결이 막히지 않는다")
    void concludesWithoutSettlementAccount() {
        // 가입 시 계좌가 필수지만 탈퇴·삭제로 비어 있을 수 있다. 계좌 때문에 체결이 무산되면 안 된다.
        given(partyReaderPort.settlementAccountSnapshot(any())).willReturn(Optional.empty());

        service.sign(command(CLIENT_ACCOUNT_ID, null));
        assertThat(service.sign(command(FREELANCER_ACCOUNT_ID, null))).isTrue();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        assertThat(contract.getSettlementAccountEnc()).isNull();
    }

    @Test
    @DisplayName("체결 알림은 받는 사람에 따라 다음 할 일이 다르다")
    void concludedNotificationDiffersByParty() {
        // 프리랜서는 이 시점에 착수금 수수료가 청구된다. 서명 직후 화면을 떠나면 모르고 지나가서
        // 체결 알림 문구에 합쳤다. 수수료 알림을 따로 보내면 두 개가 연달아 간다.
        service.sign(command(CLIENT_ACCOUNT_ID, null));
        service.sign(command(FREELANCER_ACCOUNT_ID, null));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationCreateUseCase, times(3)).create(captor.capture());

        CreateNotificationCommand toClient = captor.getAllValues().stream()
                .filter(c -> c.ownerAccountId().equals(CLIENT_ACCOUNT_ID))
                .reduce((first, second) -> second)
                .orElseThrow();
        CreateNotificationCommand toFreelancer = captor.getAllValues().stream()
                .filter(c -> c.ownerAccountId().equals(FREELANCER_ACCOUNT_ID))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(toClient.content()).contains("채팅");
        assertThat(toFreelancer.content()).contains("착수금 수수료를 결제");

        // 링크는 양쪽 다 계약서다. 결제 화면으로 보내면 계약서를 안 보고 결제하게 된다.
        assertThat(toClient.type()).isEqualTo(NotificationType.CONTRACT_SIGNED);
        assertThat(toFreelancer.type()).isEqualTo(NotificationType.CONTRACT_SIGNED);
        assertThat(toFreelancer.linkUrl()).isEqualTo(toClient.linkUrl());
    }

    @Test
    @DisplayName("알림이 실패해도 서명은 처리된다")
    void notificationFailureDoesNotBlockSigning() {
        // 알림 때문에 롤백되면 사용자는 버튼을 눌러도 아무 일이 없는 것처럼 보인다.
        given(notificationCreateUseCase.create(any())).willThrow(new RuntimeException("알림 저장 실패"));

        service.sign(command(CLIENT_ACCOUNT_ID, null));

        assertThat(signatureOf(PartyRole.CLIENT).getStatus()).isEqualTo(SignatureStatus.SIGNED);
    }

    @Test
    @DisplayName("양측이 서명해야 체결되고 그때 인원 확정·착수금이 함께 일어난다")
    void concludesOnlyWhenBothSigned() {
        assertThat(service.sign(command(CLIENT_ACCOUNT_ID, SIGNATURE_FILE_ID))).isFalse();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        verify(projectCommandUseCase, never()).confirmPosition(any());

        assertThat(service.sign(command(FREELANCER_ACCOUNT_ID, SIGNATURE_FILE_ID))).isTrue();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        verify(projectCommandUseCase).confirmPosition(contract.getPositionId());
        verify(depositSettlementUseCase).createFreelancerDeposit(any());

        // 채팅방은 커밋 뒤에 연다. ContractChatListenerTest 가 맡는다.
        verify(eventPublisher).publishEvent(any(ContractSignedEvent.class));
    }
}
