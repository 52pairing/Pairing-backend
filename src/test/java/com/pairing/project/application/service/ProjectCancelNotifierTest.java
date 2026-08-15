package com.pairing.project.application.service;

import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 프로젝트가 취소되면 클라이언트에게 통지한다. (모집 기간 만료 P46)
 *
 * <p>프리랜서 통지는 협상·매칭이 각자 맡으므로 여기서 검증하지 않는다. 이 리스너의 책임은
 * 딱 하나 — 발주자 계정을 찾아 {@code PROJECT_CANCELED} 를 보내는 것, 그리고 참조가 끊겼을 때
 * 조용히 넘어가는 것이다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectCancelNotifierTest {

    private static final Long PROJECT_ID = 8_800L;
    private static final Long CLIENT_PROFILE_ID = 55L;
    private static final Long CLIENT_ACCOUNT_ID = 777L;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ClientProfileReaderPort clientProfileReaderPort;
    @Mock
    private NotificationCreateUseCase notificationCreateUseCase;

    @InjectMocks
    private ProjectCancelNotifier notifier;

    @Test
    @DisplayName("취소 시 클라이언트 계정으로 PROJECT_CANCELED 알림을 보낸다")
    void notifiesClient() {
        // client_profile.id 를 계정 id 로 번역해 그 계정으로 알림이 가야 한다.
        Project project = mock(Project.class);
        given(project.getClientId()).willReturn(CLIENT_PROFILE_ID);
        given(project.getTitle()).willReturn("냉장고 개발");
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project));
        given(clientProfileReaderPort.findAccountId(CLIENT_PROFILE_ID)).willReturn(CLIENT_ACCOUNT_ID);

        notifier.on(new ProjectCanceledEvent(PROJECT_ID));

        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationCreateUseCase).create(captor.capture());

        CreateNotificationCommand sent = captor.getValue();
        assertThat(sent.ownerAccountId()).isEqualTo(CLIENT_ACCOUNT_ID);
        assertThat(sent.type()).isEqualTo(NotificationType.PROJECT_CANCELED);
        assertThat(sent.content()).contains("냉장고 개발");
        assertThat(sent.linkUrl()).isEqualTo("/client/projects/" + PROJECT_ID);
    }

    @Test
    @DisplayName("클라이언트 계정을 찾지 못하면 알림을 보내지 않는다 — NPE 없이 스킵")
    void skipsWhenClientAccountMissing() {
        // 프로필이 지워졌을 때. 알림만 못 보낼 뿐 터지면 안 된다.
        Project project = mock(Project.class);
        given(project.getClientId()).willReturn(CLIENT_PROFILE_ID);
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.of(project));
        given(clientProfileReaderPort.findAccountId(CLIENT_PROFILE_ID)).willReturn(null);

        notifier.on(new ProjectCanceledEvent(PROJECT_ID));

        verify(notificationCreateUseCase, never()).create(any());
    }

    @Test
    @DisplayName("프로젝트가 없으면 계정 조회도 알림도 하지 않는다")
    void skipsWhenProjectMissing() {
        given(projectRepository.findById(PROJECT_ID)).willReturn(Optional.empty());

        notifier.on(new ProjectCanceledEvent(PROJECT_ID));

        verify(clientProfileReaderPort, never()).findAccountId(any());
        verify(notificationCreateUseCase, never()).create(any());
    }
}
