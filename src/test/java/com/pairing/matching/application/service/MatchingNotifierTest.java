package com.pairing.matching.application.service;

import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 받을 계정을 못 찾았을 때 알림 도메인까지 넘기지 않는지 확인한다.
 *
 * <p>{@code ProjectDirectoryPort.findClientAccountId}는 클라이언트 프로필이 없으면 {@code null}을
 * 돌려준다. 그대로 넘기면 알림 도메인 안쪽에서 NT_003으로 터져서, 남의 도메인 에러코드로만 보이고
 * 무엇이 없었는지는 안 남는다. 그 포트를 {@code orElseThrow}로 바꾸지 않은 이유는
 * {@link ClientGradeResolver}가 같은 포트를 쓰면서 프로필이 없으면 조용히 SILVER로 떨어뜨리기
 * 때문이다 - 던지게 바꾸면 추천 라운드 생성이 실패한다.
 */
class MatchingNotifierTest {

    private NotificationCreateUseCase notificationCreateUseCase;
    private FreelancerDirectoryPort freelancerDirectoryPort;
    private ProjectDirectoryPort projectDirectoryPort;
    private MatchingNotifier notifier;

    @BeforeEach
    void setUp() {
        notificationCreateUseCase = Mockito.mock(NotificationCreateUseCase.class);
        freelancerDirectoryPort = Mockito.mock(FreelancerDirectoryPort.class);
        projectDirectoryPort = Mockito.mock(ProjectDirectoryPort.class);
        notifier = new MatchingNotifier(notificationCreateUseCase, freelancerDirectoryPort, projectDirectoryPort);
    }

    @Test
    @DisplayName("클라이언트 계정을 못 찾으면 알림을 만들지 않는다")
    void skipsWhenOwnerAccountIsMissing() {
        when(projectDirectoryPort.findClientAccountId(1L)).thenReturn(null);
        when(freelancerDirectoryPort.findCardSummary(4L)).thenReturn(cardSummary());

        notifier.notifyAccepted(request());

        verify(notificationCreateUseCase, never()).create(any());
    }

    @Test
    @DisplayName("계정을 찾으면 그 계정으로 알림을 만든다")
    void sendsToResolvedOwnerAccount() {
        when(projectDirectoryPort.findClientAccountId(1L)).thenReturn(900L);
        when(freelancerDirectoryPort.findCardSummary(4L)).thenReturn(cardSummary());

        notifier.notifyAccepted(request());

        verify(notificationCreateUseCase).create(argThat((CreateNotificationCommand command) ->
                command.ownerAccountId().equals(900L)
                        && command.title().contains("이프리")
                        && command.linkUrl().equals("/matchings/requests/55")));
    }

    private FreelancerCardSummary cardSummary() {
        return new FreelancerCardSummary("이프리", null, FreelancerGrade.SENIOR, 4.8, 12);
    }

    private MatchingRequest request() {
        return MatchingRequest.reconstitute(55L, 1L, 2L, 3L, 4L, MatchingStatus.NEGOTIATING,
                LocalDateTime.now(), LocalDateTime.now().plusDays(3), LocalDateTime.now(), null);
    }
}
