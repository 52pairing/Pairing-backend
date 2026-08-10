package com.pairing.account.application.usecase;

import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.EmployeeCount;

/**
 * 계정 상태를 바꾸는 인바운드 포트.
 *
 * <p>auth 도메인은 이 인터페이스만 호출한다. 계정 상태 전이(잠금/해제/비밀번호 교체)를
 * auth가 직접 하면 규칙이 두 도메인에 흩어진다.
 */
public interface AccountCommandUseCase {

    Long createClientAccount(CreateClientAccountCommand command);

    Long createFreelancerAccount(CreateFreelancerAccountCommand command);

    Long createSocialFreelancerAccount(CreateSocialFreelancerAccountCommand command);

    /** 로그인 성공 기록(실패 횟수 초기화 + 최종 로그인 시각). */
    Account applyLoginSuccess(Long accountId);

    /** 로그인 실패 기록. 임계치에 도달하면 계정이 잠긴다. 갱신된 계정을 반환한다. */
    Account applyLoginFailure(Long accountId, int lockThreshold);

    void changePassword(Long accountId, String newPasswordHash, boolean temporary);

    void unlock(Long accountId);

    void verifyEmail(Long accountId);

    /** 클라이언트 마이페이지(기업정보) 수정. 사업자등록번호·사업 분야는 대상이 아니다. */
    void updateClientProfile(Long accountId, String companyName, EmployeeCount employeeCount, String address);

    /** 프리랜서 마이페이지 > 매칭 설정 수정. 없으면 {@code AC_002}. */
    void updateFreelancerMatchingSettings(Long accountId, boolean aiMatchingAgreed, boolean matchingPaused);
}
