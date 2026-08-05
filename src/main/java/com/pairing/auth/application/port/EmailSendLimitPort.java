package com.pairing.auth.application.port;

import java.time.Duration;

/** 이메일 발송 횟수 제한(고정 윈도우). */
public interface EmailSendLimitPort {

    /** 발송 횟수를 1 증가시키고 현재 횟수를 반환한다. 최초 호출 시 윈도우가 시작된다. */
    int increaseAndGet(String email, Duration window);

    /** 남은 윈도우. 사용자에게 "언제 다시 시도할 수 있는지" 안내하는 데 쓴다. */
    Duration remainingWindow(String email);
}
