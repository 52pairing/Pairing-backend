package com.pairing.notification.application.port.out;

import com.pairing.notification.application.event.NotificationEvent;

/** 알림 실시간 push 발행 포트. 구현체가 STOMP {@code /topic/users/{accountId}/notifications} 로 보낸다. */
public interface NotificationEventPort {

    void publish(Long ownerAccountId, NotificationEvent event);
}
