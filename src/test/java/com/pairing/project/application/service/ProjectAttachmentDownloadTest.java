package com.pairing.project.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort;
import com.pairing.project.application.port.ProjectFileReaderPort.ProjectFileView;
import com.pairing.project.application.port.SettlementReaderPort;
import com.pairing.project.application.result.ProjectAttachment;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.model.ProjectPaymentStatus;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 첨부 다운로드의 열람 범위.
 *
 * <p>상세 응답의 CDN 경로는 랜덤 UUID 라 추측할 수 없지만, 이 API 의 {@code projectId}/{@code fileId} 는
 * <b>순차 정수</b>다. 소유자 검증이 빠지면 로그인한 아무나 1번부터 훑어 남의 첨부를 받을 수 있다.
 * 그래서 "되는 경우"보다 <b>막히는 경우</b>를 더 촘촘히 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectAttachmentDownloadTest {

    private static final Long PROJECT_ID = 7L;
    private static final Long OWNER_ACCOUNT_ID = 100L;
    private static final Long OWNER_PROFILE_ID = 900L;
    private static final Long STRANGER_ACCOUNT_ID = 200L;
    private static final Long STRANGER_PROFILE_ID = 901L;
    private static final Long ATTACHED_FILE_ID = 11L;
    private static final byte[] CONTENT = "그랩.jpg 원본".getBytes();

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ClientProfileReaderPort clientProfileReaderPort;
    @Mock
    private ProjectFileReaderPort projectFileReaderPort;
    @Mock
    private SettlementReaderPort settlementReaderPort;

    private ProjectQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProjectQueryService(projectRepository, clientProfileReaderPort,
                projectFileReaderPort, settlementReaderPort);

        given(clientProfileReaderPort.getByAccountId(OWNER_ACCOUNT_ID))
                .willReturn(new ClientProfileReaderPort.ClientProfileView(OWNER_PROFILE_ID, null, null));
        given(clientProfileReaderPort.getByAccountId(STRANGER_ACCOUNT_ID))
                .willReturn(new ClientProfileReaderPort.ClientProfileView(STRANGER_PROFILE_ID, null, null));

        given(projectRepository.findById(PROJECT_ID))
                .willReturn(Optional.of(projectWithFiles(List.of(ATTACHED_FILE_ID))));

        given(projectFileReaderPort.getAllByIds(List.of(ATTACHED_FILE_ID)))
                .willReturn(List.of(new ProjectFileView(ATTACHED_FILE_ID, "그랩.jpg", 122_700L, "project/uuid.jpg")));
        given(projectFileReaderPort.readContent(ATTACHED_FILE_ID)).willReturn(Optional.of(CONTENT));
    }

    @Test
    @DisplayName("소유자는 원본과 파일명을 받는다")
    void ownerDownloads() {
        ProjectAttachment attachment =
                service.downloadAttachment(PROJECT_ID, ATTACHED_FILE_ID, OWNER_ACCOUNT_ID);

        assertThat(attachment.content()).isEqualTo(CONTENT);
        // Content-Disposition 에 그대로 실린다. 저장될 이름이라 원본명이어야 한다.
        assertThat(attachment.originalName()).isEqualTo("그랩.jpg");
    }

    @Test
    @DisplayName("남의 프로젝트면 PJ_003 — 로그인만으로는 못 받는다")
    void rejectsStranger() {
        assertThatThrownBy(() ->
                service.downloadAttachment(PROJECT_ID, ATTACHED_FILE_ID, STRANGER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인의 프로젝트가 아닙니다");
    }

    @Test
    @DisplayName("내 프로젝트라도 거기 안 달린 fileId 면 FI_001")
    void rejectsForeignFileId() {
        // 소유한 프로젝트 하나만 있으면 projectId 를 고정해 두고 fileId 만 훑을 수 있다.
        assertThatThrownBy(() ->
                service.downloadAttachment(PROJECT_ID, 99L, OWNER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("소속이 아니면 스토리지를 아예 읽지 않는다")
    void doesNotTouchStorageWhenNotAttached() {
        // 읽고 나서 버리면 응답 시간 차이로 그 fileId 의 실존 여부가 새어 나간다.
        assertThatThrownBy(() -> service.downloadAttachment(PROJECT_ID, 99L, OWNER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class);

        verify(projectFileReaderPort, never()).readContent(any());
    }

    @Test
    @DisplayName("남의 프로젝트면 첨부 소속 조회조차 하지 않는다")
    void doesNotTouchFilesWhenNotOwner() {
        assertThatThrownBy(() ->
                service.downloadAttachment(PROJECT_ID, ATTACHED_FILE_ID, STRANGER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class);

        verify(projectFileReaderPort, never()).readContent(any());
        verify(projectFileReaderPort, never()).getAllByIds(any());
    }

    @Test
    @DisplayName("목록에는 있는데 스토리지에서 못 읽으면 FI_001")
    void rejectsWhenStorageEmpty() {
        // 파일이 지워졌거나 스토리지가 흔들린 경우. 빈 바이트를 200 으로 내보내면 안 된다.
        given(projectFileReaderPort.readContent(ATTACHED_FILE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.downloadAttachment(PROJECT_ID, ATTACHED_FILE_ID, OWNER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("파일을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("fileId 가 null 이면 FI_001 — NPE 로 500 이 나가지 않는다")
    void rejectsNullFileId() {
        assertThatThrownBy(() -> service.downloadAttachment(PROJECT_ID, null, OWNER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("첨부가 없는 프로젝트는 무엇을 요청해도 FI_001")
    void rejectsWhenProjectHasNoFiles() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(projectWithFiles(List.of())));

        assertThatThrownBy(() ->
                service.downloadAttachment(PROJECT_ID, ATTACHED_FILE_ID, OWNER_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class);
    }

    /** 소유자만 다르게 두면 되는 테스트라 나머지 값은 최소로 채운다. */
    private Project projectWithFiles(List<Long> fileIds) {
        return Project.reconstitute(PROJECT_ID, OWNER_PROFILE_ID, "냉장고 개발", LocalDate.of(2026, 9, 1),
                false, 4, PeriodUnit.MONTH, 40_000_000L, WorkStyle.REMOTE, WorkForm.FULL_TIME,
                null, "상황", "담당 업무", "세부 범위", null,
                ProjectStatus.RECRUITING, ProjectPaymentStatus.DEPOSIT_PAID,
                1, 0,
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), 0, 0, 0,
                LocalDateTime.now(), null, null, null, LocalDateTime.now(),
                List.of(), fileIds);
    }
}
