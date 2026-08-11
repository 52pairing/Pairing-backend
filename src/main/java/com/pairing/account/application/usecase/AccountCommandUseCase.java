package com.pairing.account.application.usecase;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.application.command.CardCommand;
import com.pairing.account.application.command.CreateClientAccountCommand;
import com.pairing.account.application.command.CreateFreelancerAccountCommand;
import com.pairing.account.application.command.CreateSocialFreelancerAccountCommand;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.domain.model.PaymentMethod;

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

    /**
     * 클라이언트 마이페이지(기업정보) 수정. 사업자등록번호·사업 분야·업무이메일·담당자명은 대상이 아니다.
     *
     * <p>{@code logoFileId} 가 null 이면 기존 로고를 유지한다.
     */
    void updateClientProfile(Long accountId, String companyName, EmployeeCount employeeCount, String phone,
                             String address, Long logoFileId);

    /** 프리랜서 마이페이지 > 매칭 설정 수정. 없으면 {@code AC_002}. */
    void updateFreelancerMatchingSettings(Long accountId, boolean aiMatchingAgreed, boolean matchingPaused);

    /** 프리랜서 마이페이지 기본 정보 수정. 전화번호(계정) + 주소·프로필사진·AI매칭동의(프로필)를 함께 반영한다. */
    void updateFreelancerProfile(Long accountId, String phone, String address, Long profileFileId,
                                 boolean aiMatchingAgreed);

    /**
     * 마이페이지 &gt; 결제수단 카드 교체. 가입 시 만들어진 카드 1건을 수정한다(신규 등록·삭제 없음).
     *
     * <p>카드번호는 저장 직전에 암호화되고 끝 4자리만 따로 남는다. 없으면 {@code AC_007}.
     */
    PaymentMethod updateCard(Long accountId, CardCommand command);

    /** 마이페이지 &gt; 결제수단 정산 계좌 교체. 은행 코드가 유효하지 않으면 {@code AC_006}, 없으면 {@code AC_007}. */
    PaymentMethod updateBankAccount(Long accountId, BankAccountCommand command);
}
