package com.pairing.matching.application.service;

import com.pairing.matching.application.event.MatchingNotificationRequested;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림 발송 실패가 리스너 밖으로 새지 않는지 확인한다.
 *
 * <p>이 리스너의 존재 이유가 "알림 실패로 매칭이 롤백되지 않게" 하는 것이라(2026-08-13 알림 도메인
 * 리포트) 예외가 밖으로 나가면 그 목적이 깨진다. 리스너에 {@code @Transactional}을 붙이거나
 * try-catch를 지우면 이 테스트가 깨진다.
 */
class MatchingNotificationListenerTest {

    private static final Long REQUEST_ID = 55L;

    private MatchingRequestRepository matchingRequestRepository;
    private MatchingNotifier matchingNotifier;
    private MatchingNotificationListener listener;

    @BeforeEach
    void setUp() {
        matchingRequestRepository = Mockito.mock(MatchingRequestRepository.class);
        matchingNotifier = Mockito.mock(MatchingNotifier.class);
        listener = new MatchingNotificationListener(matchingRequestRepository, matchingNotifier);
    }

    @Test
    @DisplayName("발송이 실패해도 예외가 리스너 밖으로 나가지 않는다")
    void swallowsNotifierFailure() {
        when(matchingRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request()));
        Mockito.doThrow(new IllegalStateException("알림 저장 실패"))
                .when(matchingNotifier).notifyAccepted(any());

        assertThatCode(() -> listener.on(MatchingNotificationRequested.accepted(REQUEST_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대상 요청이 없으면 발송을 시도하지 않는다")
    void skipsWhenRequestIsGone() {
        when(matchingRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        listener.on(MatchingNotificationRequested.accepted(REQUEST_ID));

        verify(matchingNotifier, never()).notifyAccepted(any());
    }

    @Test
    @DisplayName("종류에 맞는 발송 메서드를 부른다")
    void dispatchesByKind() {
        MatchingRequest request = request();
        when(matchingRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        listener.on(MatchingNotificationRequested.requested(REQUEST_ID, "페어링 리뉴얼"));
        listener.on(MatchingNotificationRequested.accepted(REQUEST_ID));
        listener.on(MatchingNotificationRequested.rejected(REQUEST_ID));
        listener.on(MatchingNotificationRequested.expired(REQUEST_ID));

        verify(matchingNotifier).notifyRequested(eq(request), eq("페어링 리뉴얼"));
        verify(matchingNotifier).notifyAccepted(request);
        verify(matchingNotifier).notifyRejected(request);
        verify(matchingNotifier).notifyExpired(request);
    }

    private MatchingRequest request() {
        return MatchingRequest.reconstitute(REQUEST_ID, 1L, 2L, 3L, 4L, MatchingStatus.REQUEST_PENDING,
                LocalDateTime.now(), LocalDateTime.now().plusDays(3), null, null);
    }
}
