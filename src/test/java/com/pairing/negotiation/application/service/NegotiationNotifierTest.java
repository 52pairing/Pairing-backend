package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.event.NegotiationNotificationRequested;
import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import com.pairing.negotiation.application.port.out.PartyProfilePort;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** 협상 알림 3종: 양측 발송·문구 분기·실패 격리 검증. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NegotiationNotifierTest {

    private static final Long NEGOTIATION_ID = 28L;
    private static final Long PROJECT_ID = 7L;
    private static final Long CLIENT_PROFILE_ID = 3L;
    private static final Long FREELANCER_PROFILE_ID = 5L;
    private static final Long CLIENT_ACCOUNT_ID = 100L;
    private static final Long FREELANCER_ACCOUNT_ID = 200L;

    @Mock
    private NotificationCreateUseCase notificationCreateUseCase;
    @Mock
    private PartyProfilePort partyProfilePort;
    @Mock
    private PartyNameReaderPort partyNameReaderPort;
    @Mock
    private ProjectReaderPort projectReaderPort;

    @InjectMocks
    private NegotiationNotifier notifier;

    @BeforeEach
    void setUp() {
        given(projectReaderPort.findById(PROJECT_ID)).willReturn(Optional.of(new ProjectReaderPort.ProjectView(
                PROJECT_ID, CLIENT_PROFILE_ID, "커머스 리뉴얼", null, null, null, null, false, null, null)));
        given(partyProfilePort.findAccountIdByClientProfileId(CLIENT_PROFILE_ID))
                .willReturn(Optional.of(CLIENT_ACCOUNT_ID));
        given(partyProfilePort.findAccountIdByFreelancerProfileId(FREELANCER_PROFILE_ID))
                .willReturn(Optional.of(FREELANCER_ACCOUNT_ID));
        given(partyNameReaderPort.findFreelancerName(FREELANCER_PROFILE_ID)).willReturn(Optional.of("김승재"));
    }

    @Test
    @DisplayName("협상 시작: 양측에 각각 한 번, 상대가 누구인지로 문구가 갈린다")
    void notifiesBothPartiesOnStart() {
        notifier.on(event(NotificationType.NEGOTIATION_STARTED, 0, null));

        List<CreateNotificationCommand> sent = captureSent(2);
        CreateNotificationCommand toClient = sent.get(0);
        CreateNotificationCommand toFreelancer = sent.get(1);

        assertThat(toClient.ownerAccountId()).isEqualTo(CLIENT_ACCOUNT_ID);
        assertThat(toClient.type()).isEqualTo(NotificationType.NEGOTIATION_STARTED);
        assertThat(toClient.content()).contains("김승재 님과의");
        assertThat(toFreelancer.ownerAccountId()).isEqualTo(FREELANCER_ACCOUNT_ID);
        assertThat(toFreelancer.content()).contains("커머스 리뉴얼 프로젝트의");
        // 링크는 받는 사람 역할에 맞춰 갈린다. 프론트 협상방 라우트가 역할별로 나뉘어 있고
        // 프로젝트 ID 까지 필요해서다(양쪽에 /negotiations/{id} 를 보내 404 가 나던 것을 고쳤다).
        assertThat(toClient.linkUrl()).isEqualTo("/client/projects/7/negotiation/28");
        assertThat(toFreelancer.linkUrl()).isEqualTo("/freelancer/projects/7/negotiation/28");
    }

    @Test
    @DisplayName("새 AI 제안: 몇 라운드 제안인지 문구에 들어간다")
    void includesRoundNumberOnProposed() {
        notifier.on(event(NotificationType.NEGOTIATION_PROPOSED, 3, null));

        assertThat(captureSent(2)).allSatisfy(command -> {
            assertThat(command.title()).isEqualTo("새로운 AI 제안이 도착했습니다.");
            assertThat(command.content()).contains("협상 3라운드 제안이 도착했어요");
        });
    }

    @Test
    @DisplayName("결렬: 종료 사유가 문구에 들어간다")
    void includesEndReasonOnFailed() {
        notifier.on(event(NotificationType.NEGOTIATION_FAILED, 15, "라운드 상한(15회) 소진으로 자동 결렬"));

        assertThat(captureSent(2)).allSatisfy(command -> {
            assertThat(command.title()).isEqualTo("협상이 결렬되었습니다.");
            assertThat(command.content()).endsWith("라운드 상한(15회) 소진으로 자동 결렬");
        });
    }

    @Test
    @DisplayName("한쪽 계정을 못 찾아도 상대는 알림을 받는다")
    void sendsToOtherPartyWhenOneAccountMissing() {
        given(partyProfilePort.findAccountIdByClientProfileId(CLIENT_PROFILE_ID)).willReturn(Optional.empty());

        notifier.on(event(NotificationType.NEGOTIATION_STARTED, 0, null));

        assertThat(captureSent(1))
                .singleElement()
                .satisfies(command -> assertThat(command.ownerAccountId()).isEqualTo(FREELANCER_ACCOUNT_ID));
    }

    @Test
    @DisplayName("알림 발송이 터져도 예외가 협상으로 새지 않고, 남은 한쪽은 발송된다")
    void swallowsFailureAndKeepsGoing() {
        willThrow(new RuntimeException("알림 저장 실패"))
                .given(notificationCreateUseCase)
                .create(org.mockito.ArgumentMatchers.argThat(
                        command -> command != null && CLIENT_ACCOUNT_ID.equals(command.ownerAccountId())));

        assertThatCode(() -> notifier.on(event(NotificationType.NEGOTIATION_STARTED, 0, null)))
                .doesNotThrowAnyException();

        // 클라 쪽이 터졌어도 프리랜서 쪽 호출은 그대로 시도된다(둘 다 시도 = 2회).
        verify(notificationCreateUseCase, times(2)).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("대상 조회가 터져도 예외가 협상으로 새지 않는다")
    void swallowsLookupFailure() {
        given(projectReaderPort.findById(PROJECT_ID)).willThrow(new RuntimeException("프로젝트 조회 실패"));

        assertThatCode(() -> notifier.on(event(NotificationType.NEGOTIATION_FAILED, 2, "협상 포기")))
                .doesNotThrowAnyException();
    }

    private NegotiationNotificationRequested event(NotificationType type, int roundNo, String endReason) {
        return new NegotiationNotificationRequested(NEGOTIATION_ID, type, PROJECT_ID, FREELANCER_PROFILE_ID,
                roundNo, endReason);
    }

    /** 발송된 알림을 발송 순서대로(클라 → 프리) 돌려준다. */
    private List<CreateNotificationCommand> captureSent(int expectedCount) {
        ArgumentCaptor<CreateNotificationCommand> captor =
                ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationCreateUseCase, times(expectedCount)).create(captor.capture());
        return captor.getAllValues();
    }
}
