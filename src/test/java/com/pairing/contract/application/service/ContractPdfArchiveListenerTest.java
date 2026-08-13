package com.pairing.contract.application.service;

import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.application.port.ContractArchivePort;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 체결된 계약서를 파일로 굳히는 규칙.
 *
 * <p>굳히지 않으면 조항 문구를 고쳤을 때 이미 체결된 계약서까지 새 양식으로 바뀐다.
 * 반대로 굳히다 실패했다고 체결이 되돌아가서도 안 된다 — 파일이 없으면 예전처럼 그리면 된다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractPdfArchiveListenerTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long ARCHIVED_FILE_ID = 77L;
    private static final byte[] PDF = "%PDF-1.4 ...".getBytes();

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractQueryUseCase contractQueryUseCase;
    @Mock
    private ContractArchivePort archivePort;

    private ContractPdfArchiveListener listener;
    private Contract contract;

    @BeforeEach
    void setUp() {
        listener = new ContractPdfArchiveListener(contractRepository, contractQueryUseCase, archivePort);
        contract = contract();

        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.of(contract));
        given(contractQueryUseCase.renderPdf(any(), any())).willReturn(PDF);
        given(archivePort.archive(any(), any(), any())).willReturn(ARCHIVED_FILE_ID);
    }

    @Test
    @DisplayName("체결된 계약서를 파일로 굳히고 계약에 연결한다")
    void archivesOnConclusion() {
        listener.on(new ContractSignedEvent(CONTRACT_ID, 1L, 10L, 200L));

        assertThat(contract.getPdfFileId()).isEqualTo(ARCHIVED_FILE_ID);
        verify(contractRepository).updateState(contract);
    }

    @Test
    @DisplayName("이미 굳힌 계약은 다시 저장하지 않는다")
    void skipsWhenAlreadyArchived() {
        // 이벤트가 중복 전달돼도 파일이 두 개 생기면 안 된다.
        contract.attachPdf(ARCHIVED_FILE_ID);

        listener.on(new ContractSignedEvent(CONTRACT_ID, 1L, 10L, 200L));

        verify(archivePort, never()).archive(any(), any(), any());
    }

    @Test
    @DisplayName("보관이 실패해도 예외가 밖으로 나가지 않는다")
    void swallowsArchiveFailure() {
        // 커밋이 끝난 뒤라 체결을 되돌릴 수 없다. pdf_file_id 가 비면 조회가 렌더링으로 떨어진다.
        willThrow(new RuntimeException("스토리지 장애")).given(archivePort)
                .archive(any(), any(), any());

        assertThatCode(() -> listener.on(new ContractSignedEvent(CONTRACT_ID, 1L, 10L, 200L)))
                .doesNotThrowAnyException();

        assertThat(contract.getPdfFileId()).isNull();
    }

    @Test
    @DisplayName("계약을 못 찾으면 조용히 끝낸다")
    void skipsWhenContractGone() {
        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.empty());

        assertThatCode(() -> listener.on(new ContractSignedEvent(CONTRACT_ID, 1L, 10L, 200L)))
                .doesNotThrowAnyException();
        verify(archivePort, never()).archive(any(), any(), any());
    }

    private Contract contract() {
        return Contract.create(300L, 1L, 10L, 100L, 200L,
                900_001L, 900_002L, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
    }
}
