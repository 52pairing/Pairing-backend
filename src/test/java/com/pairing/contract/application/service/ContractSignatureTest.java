package com.pairing.contract.application.service;

import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.contract.application.command.SignContractCommand;
import com.pairing.contract.application.port.ContractFileReaderPort;
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
import com.pairing.project.application.usecase.ProjectCommandUseCase;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
    private ChatActivationUseCase chatActivationUseCase;
    @Mock
    private DepositSettlementUseCase depositSettlementUseCase;
    @Mock
    private FreelancerGradeReaderPort freelancerGradeReaderPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ContractCommandService service;
    private Contract contract;

    @BeforeEach
    void setUp() {
        service = new ContractCommandService(contractRepository, contractFileReaderPort,
                projectCommandUseCase, chatActivationUseCase, depositSettlementUseCase,
                freelancerGradeReaderPort, eventPublisher);

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
    @DisplayName("양측이 서명해야 체결되고 그때 인원 확정·채팅방·착수금이 함께 일어난다")
    void concludesOnlyWhenBothSigned() {
        assertThat(service.sign(command(CLIENT_ACCOUNT_ID, SIGNATURE_FILE_ID))).isFalse();
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGN_PENDING);
        verify(projectCommandUseCase, never()).confirmPosition(any());

        assertThat(service.sign(command(FREELANCER_ACCOUNT_ID, SIGNATURE_FILE_ID))).isTrue();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.SIGNED);
        verify(projectCommandUseCase).confirmPosition(contract.getPositionId());
        verify(chatActivationUseCase).openForSignedContract(contract.getNegotiationId());
        verify(depositSettlementUseCase).createFreelancerDeposit(any());
    }
}
