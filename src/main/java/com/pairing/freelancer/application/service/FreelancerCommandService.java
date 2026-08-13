package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.auth.application.usecase.EmailVerificationUseCase;
import com.pairing.auth.domain.model.VerificationPurpose;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.freelancer.application.command.FreelancerProfileUpdateCommand;
import com.pairing.freelancer.application.result.FreelancerMyPageResult;
import com.pairing.freelancer.application.usecase.FreelancerCommandUseCase;
import com.pairing.freelancer.application.usecase.FreelancerQueryUseCase;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class FreelancerCommandService implements FreelancerCommandUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final FreelancerQueryUseCase freelancerQueryUseCase;
    private final EmailVerificationUseCase emailVerificationUseCase;

    @Override
    public FreelancerMyPageResult updateMyPage(FreelancerProfileUpdateCommand command) {
        Account account = accountQueryUseCase.getById(command.accountId());
        if (!emailVerificationUseCase.isVerified(account.getEmail(), VerificationPurpose.PROFILE_UPDATE)) {
            throw new BusinessException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        accountCommandUseCase.updateFreelancerProfile(command.accountId(), command.phone(), command.address(),
                command.profileFileId(), command.aiMatchingAgreed());

        FreelancerMyPageResult result = freelancerQueryUseCase.findMyPage(command.accountId());

        // 인증 마커는 1회용이다. 남겨 두면 같은 인증으로 여러 번 수정할 수 있다.
        // 소비는 맨 마지막에 한다. 조회에서 예외가 나면 저장은 롤백되는데 마커(Redis)는 롤백되지 않아,
        // 먼저 지우면 "저장은 안 됐는데 인증만 날아간" 상태가 된다.
        emailVerificationUseCase.clearVerification(account.getEmail(), VerificationPurpose.PROFILE_UPDATE);

        return result;
    }
}
