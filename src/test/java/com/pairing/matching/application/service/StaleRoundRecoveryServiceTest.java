package com.pairing.matching.application.service;

import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 멈춘 회차 복구. 실제 DB/스레드 없이 포트만 목으로 대체해 검증한다 - 이 서비스는
 * {@link MatchingRoundFiller}를 반복 호출하는 얇은 오케스트레이션이라 SpringBootTest 없이도
 * 충분히 검증된다({@code EmbeddingReindexServiceTest}와 같은 이유).
 *
 * <p>2026-08-18에 완료/실패 알림이 빠져 있던 걸 발견해 추가했다 - 알림이 없으면 프론트는
 * "준비중" 화면을 새로고침 전까지 갱신하지 않는다.
 */
class StaleRoundRecoveryServiceTest {

    private final MatchingRoundRepository matchingRoundRepository = Mockito.mock(MatchingRoundRepository.class);
    private final MatchingRoundCreationService matchingRoundCreationService =
            Mockito.mock(MatchingRoundCreationService.class);
    private final ProjectDirectoryPort projectDirectoryPort = Mockito.mock(ProjectDirectoryPort.class);
    private final NotificationCreateUseCase notificationCreateUseCase = Mockito.mock(NotificationCreateUseCase.class);

    private final MatchingRoundFiller matchingRoundFiller =
            new MatchingRoundFiller(matchingRoundRepository, matchingRoundCreationService);
    private final MatchingRoundCompletionNotifier completionNotifier =
            new MatchingRoundCompletionNotifier(notificationCreateUseCase, projectDirectoryPort);

    private final StaleRoundRecoveryService service =
            new StaleRoundRecoveryService(matchingRoundRepository, matchingRoundFiller, completionNotifier);

    @Test
    @DisplayName("멈춘 회차를 되살리면 완료 알림을 보낸다")
    void notifiesCompletionWhenStaleRoundIsRecovered() {
        MatchingRound stale = round(1L, 10L, 100L, MatchingRoundStatus.RUNNING);
        MatchingRound completed = round(1L, 10L, 100L, MatchingRoundStatus.COMPLETED);
        when(matchingRoundRepository.findStaleRunning(any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(matchingRoundRepository.findByIdForUpdate(1L)).thenReturn(java.util.Optional.of(stale));
        when(matchingRoundCreationService.fillCandidates(stale)).thenReturn(completed);
        when(projectDirectoryPort.findClientAccountId(10L)).thenReturn(999L);

        service.recoverStaleRounds();

        verify(notificationCreateUseCase).create(argThat((CreateNotificationCommand cmd) ->
                cmd.ownerAccountId().equals(999L)
                        && cmd.type() == NotificationType.MATCHING_RECOMMENDED
                        && cmd.linkUrl().equals("/matchings/positions/100/candidates")));
    }

    @Test
    @DisplayName("복구 중 다시 실패하면 회차를 FAILED로 닫고 실패 알림을 보낸다")
    void notifiesFailureWhenRecoveryFailsAgain() {
        MatchingRound stale = round(2L, 20L, 200L, MatchingRoundStatus.RUNNING);
        when(matchingRoundRepository.findStaleRunning(any(LocalDateTime.class))).thenReturn(List.of(stale));
        when(matchingRoundRepository.findByIdForUpdate(2L)).thenReturn(java.util.Optional.of(stale));
        when(matchingRoundCreationService.fillCandidates(stale)).thenThrow(new RuntimeException("AI 서버 장애"));
        when(matchingRoundRepository.findById(2L)).thenReturn(java.util.Optional.of(stale));
        when(matchingRoundRepository.save(any(MatchingRound.class)))
                .thenReturn(round(2L, 20L, 200L, MatchingRoundStatus.FAILED));
        when(projectDirectoryPort.findClientAccountId(20L)).thenReturn(888L);

        service.recoverStaleRounds();

        verify(notificationCreateUseCase).create(argThat((CreateNotificationCommand cmd) ->
                cmd.ownerAccountId().equals(888L)
                        && cmd.type() == NotificationType.MATCHING_RECOMMENDED
                        && cmd.title().equals("추천 후보를 만들지 못했습니다.")));
    }

    @Test
    @DisplayName("멈춘 회차가 없으면 알림도 보내지 않는다")
    void sendsNoNotificationWhenNothingIsStale() {
        when(matchingRoundRepository.findStaleRunning(any(LocalDateTime.class))).thenReturn(List.of());

        service.recoverStaleRounds();

        verify(notificationCreateUseCase, never()).create(any());
    }

    private static MatchingRound round(Long id, Long projectId, Long positionId, MatchingRoundStatus status) {
        return MatchingRound.reconstitute(id, projectId, positionId, 1, RecommendationType.INITIAL,
                null, 0L, 1, 3, false, status);
    }
}
