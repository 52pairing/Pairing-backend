package com.pairing.account.application.usecase;

import com.pairing.account.application.result.WithdrawalEligibilityResult;

/**
 * 탈퇴 가능 여부 판정. (R31)
 *
 * <p><b>{@code AccountQueryUseCase} 에 두지 않은 이유가 있다.</b> 이 판정은 프로젝트·계약·정산을
 * 읽어야 하는데, 그 도메인들은 반대로 계정을 읽는다. 계정 조회 서비스가 프로젝트를 의존하면
 * {@code account -> project -> account} 순환이 생겨 컨텍스트가 뜨지 않는다.
 *
 * <p>그래서 "계정을 읽는 곳"이 아니라 "여러 도메인을 모아 판정하는 곳"으로 분리했다.
 * 화면 진입 조회와 실제 탈퇴가 <b>같은 판정을 공유</b>하는 게 이 인터페이스의 존재 이유다.
 */
public interface WithdrawalEligibilityUseCase {

    WithdrawalEligibilityResult getWithdrawalEligibility(Long accountId);
}
