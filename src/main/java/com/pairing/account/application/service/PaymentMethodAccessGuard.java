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
 * 결제수단 <b>변경</b>의 관문. 이메일 인증을 마쳤는지 확인한다.
 *
 * <p><b>조회는 막지 않는다.</b> 한때 조회까지 막았지만 — 마스킹해도 은행명·예금주·끝 4자리가
 * 단서가 된다는 이유였다 — 그러면 수수료 결제 화면이 함께 막힌다. 그 화면도 결제할 카드를 고르려고
 * 같은 API 를 부르기 때문에, 결제하려는 사람이 매번 이메일 인증을 거쳐야 했다. 정작 막아야 할 위험은
 * 계정을 잠깐 빌린 사람이 <b>정산 계좌를 자기 것으로 바꿔치기</b>하는 것이고, 그건 수정만 막으면 된다.
 *
 * <p><b>마커를 소비하지 않는다.</b> 프로필 수정은 저장 후 {@code clearVerification} 으로 1회용으로
 * 쓰지만, 여기서 같은 방식을 쓰면 카드를 고치고 계좌를 고치는 것만으로 인증을 두 번 요구하게 된다.
 * "결제수단 화면에서 한 번"이라는 요구가 곧 시간 기반 세션이라, 유효 시간은 마커
 * TTL({@code app.auth.verified-marker-ttl}) 에 맡긴다.
 *
 * <p><b>조회에는 절대 걸지 말 것.</b> {@code AccountQueryService.findMyPaymentMethods} 는 마이페이지
 * 말고도 세 곳이 쓴다 — 수수료 결제 화면, 계약서가 을(프리랜서)의 정산 계좌를 적을 때
 * ({@code ContractPartyReaderAdapter}), 정산 목록이 "신한카드 **** 1234" 표기를 만들 때
 * ({@code SettlementController}). 어디에 걸든 이들이 함께 막힌다. 특히 계약서는 <b>상대방이 조회할
 * 때도</b> 프리랜서의 계좌를 읽어서, 보는 사람 기준의 인증 마커로는 판정 자체가 성립하지 않는다.
 *
 * <p>관문을 서비스가 아니라 컨트롤러에서 부르는 것도 같은 이유다. 인증이 필요한 건 "결제수단을
 * <b>바꾸는 행위</b>"이지 "결제수단 데이터"가 아니다.
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
