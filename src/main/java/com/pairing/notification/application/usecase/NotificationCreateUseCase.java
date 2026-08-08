package com.pairing.notification.application.usecase;

import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.result.NotificationResult;

/**
 * 다른 도메인이 알림을 만들 때 쓰는 인바운드 포트다. (매칭/협상/계약/정산/문의 등)
 *
 * <p>이벤트가 발생한 도메인의 서비스가 자기 트랜잭션 안에서 이 유스케이스를 직접 호출한다.
 * 저장과 동시에 {@code /topic/users/{ownerAccountId}/notifications} 로 실시간 push 된다.
 */
public interface NotificationCreateUseCase {

    /** {@code ownerAccountId}/{@code type}/{@code title} 이 비어있으면 {@code NT_003}. */
    NotificationResult create(CreateNotificationCommand command);
}
