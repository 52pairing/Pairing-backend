package com.pairing.account.application.scheduler;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 회원 개인정보 파기. (개인정보 보관 1년)
 *
 * <p>탈퇴할 때 이메일·휴대폰은 이미 더미로 갈아엎고, 재가입 제한(30일) 판정용 해시만 남겨 둔다.
 * 그 해시를 보관 기한이 지난 뒤 지우는 것이 이 배치다. 없으면 "1년 보관" 이 문서에만 있고
 * 실제로는 영구 보관이 된다.
 *
 * <p>스케줄링 활성화({@code @EnableScheduling})는 {@code ProjectSchedulingConfig} 가 전역으로 켜둔다.
 *
 * <p>인스턴스를 여러 대로 늘리면 같은 작업이 중복 실행된다. 파기는 여러 번 돌아도 결과가 같아서
 * (이미 지운 계정은 {@code purgeAt} 이 비어 다시 잡히지 않는다) 지금은 문제가 되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AccountPurgeScheduler {

    private final AccountCommandUseCase accountCommandUseCase;

    /**
     * 기본은 매일 새벽 4시.
     *
     * <p>하루에 한 번이면 충분하다. 보관 기한이 1년인데 몇 시간 늦게 지운다고 달라지지 않는다.
     * 대신 사용자가 적은 시간대에 돌려 다른 조회와 겹치지 않게 한다.
     */
    @Scheduled(cron = "${account.purge.cron:0 0 4 * * *}")
    public void purgeExpiredPersonalData() {
        accountCommandUseCase.purgeExpiredPersonalData();
    }
}
