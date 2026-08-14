package com.pairing.account.application.service;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 마이페이지 &gt; 결제수단 화면의 관문. 이메일 인증을 마쳤는지 확인한다.
 *
 * <p><b>수정만이 아니라 조회부터 막는다.</b> 마스킹해서 내려도 은행명·예금주·끝 4자리가 함께 보여,
 * 계정을 잠깐 빌린 사람에게 단서가 된다.
 *
 * <p><b>마커를 소비하지 않는다.</b> 프로필 수정은 저장 후 {@code clearVerification} 으로 1회용으로
 * 쓰지만, 여기서 같은 방식을 쓰면 목록을 여는 순간 인증이 날아가 이어지는 수정이 막힌다. 카드를 고치고
 * 계좌를 고치는 것만으로 인증을 세 번 요구하게 된다. "탭 진입 시 한 번"이라는 요구가 곧 시간 기반
 * 세션이라, 유효 시간은 마커 TTL({@code app.auth.verified-marker-ttl}) 에 맡긴다.
 *
 * <p><b>왜 {@code AccountQueryService.findMyPaymentMethods} 가 아니라 컨트롤러에서 부르는가.</b>
 * 그 메서드는 마이페이지 말고 두 곳이 더 쓴다 — 계약서가 을(프리랜서)의 정산 계좌를 적을 때
 * ({@code ContractPartyReaderAdapter}), 정산 목록이 "신한카드 **** 1234" 표기를 만들 때
 * ({@code SettlementController}). 서비스에 관문을 두면 그 둘이 함께 막힌다. 특히 계약서는
 * <b>상대방이 조회할 때도</b> 프리랜서의 계좌를 읽어서, 보는 사람 기준의 인증 마커로는 판정 자체가
 * 성립하지 않는다. 관문이 필요한 건 "결제수단 화면"이지 "결제수단 조회 기능"이 아니다.
 */
@Component
@RequiredArgsConstructor
public class PaymentMethodAccessGuard {

    private final AccountRepository accountRepository;
    private final EmailVerificationUseCase emailVerificationUseCase;

    /** 인증을 안 마쳤으면 {@code AU_006}. 프론트는 이 코드를 받으면 인증 화면부터 띄운다. */
    public void requireVerified(Long accountId) {
        String email = accountRepository.findById(accountId)
                .map(Account::getEmail)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (!emailVerificationUseCase.isVerified(email, VerificationPurpose.PAYMENT_METHOD)) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
    }
}
