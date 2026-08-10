package com.pairing.contract.infrastructure;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.BankCode;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.contract.application.port.ContractPartyReaderPort;
import com.pairing.global.port.out.DataEncryptionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ContractPartyReaderPort 구현. account 도메인의 인바운드 포트를 호출한다.
 *
 * <p>이름·연락처는 프로필이 아니라 계정에 있다. 프로필 -&gt; accountId -&gt; 계정 순으로 두 번 탄다.
 * 계약서에 이름이 비면 곤란하지만, 조회 실패로 계약 조회 전체를 막는 것도 과하다. 빈 뷰를 돌려주고
 * 화면이 판단하게 둔다.
 */
@Component
@RequiredArgsConstructor
public class ContractPartyReaderAdapter implements ContractPartyReaderPort {

    private final AccountQueryUseCase accountQueryUseCase;
    private final DataEncryptionPort dataEncryptionPort;

    @Override
    public String findClientName(Long clientProfileId) {
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(ClientProfile::getCompanyName)
                .orElse(null);
    }

    @Override
    public String findFreelancerName(Long freelancerProfileId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .map(FreelancerProfile::getAccountId)
                .map(accountQueryUseCase::getById)
                .map(Account::getName)
                .orElse(null);
    }

    @Override
    public ClientParty findClient(Long clientProfileId) {
        return accountQueryUseCase.findClientProfileById(clientProfileId)
                .map(profile -> {
                    Account account = accountQueryUseCase.getById(profile.getAccountId());
                    return new ClientParty(
                            profile.getAccountId(),
                            profile.getCompanyName(),
                            profile.getBusinessNo(),
                            account.getName(),
                            profile.getAddress(),
                            account.getPhone());
                })
                .orElse(ClientParty.EMPTY);
    }

    @Override
    public FreelancerParty findFreelancer(Long freelancerProfileId) {
        return accountQueryUseCase.findFreelancerProfileById(freelancerProfileId)
                .map(profile -> {
                    Account account = accountQueryUseCase.getById(profile.getAccountId());
                    return bankAccount(profile.getAccountId())
                            .map(bank -> new FreelancerParty(
                                    profile.getAccountId(), account.getName(), account.getPhone(),
                                    bankName(bank.getBankCode()),
                                    dataEncryptionPort.decrypt(bank.getAccountNoEnc()),
                                    bank.getAccountHolder()))
                            .orElseGet(() -> new FreelancerParty(
                                    profile.getAccountId(), account.getName(), account.getPhone(),
                                    null, null, null));
                })
                .orElse(FreelancerParty.EMPTY);
    }

    /** 가입 시 계좌가 필수라 보통 한 건 있다. 탈퇴·삭제로 비어 있으면 계좌 칸만 빈다. */
    private Optional<PaymentMethod> bankAccount(Long accountId) {
        return accountQueryUseCase.findMyPaymentMethods(accountId).stream()
                .filter(method -> method.getMethodType() == PaymentMethodType.BANK_ACCOUNT)
                .findFirst();
    }

    /** DB 에는 금융결제원 코드("088")가 들어 있다. 계약서에는 은행 이름을 적는다. */
    private String bankName(String bankCode) {
        return BankCode.find(bankCode).map(BankCode::getLabel).orElse(bankCode);
    }
}
