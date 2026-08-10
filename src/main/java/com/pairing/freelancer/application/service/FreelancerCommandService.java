package com.pairing.freelancer.application.service;

import com.pairing.account.application.usecase.AccountCommandUseCase;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.exception.AccountErrorCode;
import com.pairing.freelancer.application.command.FreelancerProfileUpdateCommand;
import com.pairing.freelancer.application.result.FreelancerMyPageResult;
import com.pairing.freelancer.application.usecase.FreelancerCommandUseCase;
import com.pairing.freelancer.application.usecase.FreelancerQueryUseCase;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class FreelancerCommandService implements FreelancerCommandUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final AccountCommandUseCase accountCommandUseCase;
    private final FreelancerQueryUseCase freelancerQueryUseCase;
    private final PasswordEncoder passwordEncoder;

    @Override
    public FreelancerMyPageResult updateMyPage(FreelancerProfileUpdateCommand command) {
        Account account = accountQueryUseCase.getById(command.accountId());
        // 소셜 전용 계정은 비밀번호가 없어 matches() 가 NPE를 낸다. 이 경우 확인을 건너뛴다.
        if (!account.isSocialOnly()
                && !passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new BusinessException(AccountErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        accountCommandUseCase.updateFreelancerProfile(command.accountId(), command.phone(), command.address(),
                command.profileFileId(), command.aiMatchingAgreed());
        return freelancerQueryUseCase.findMyPage(command.accountId());
    }
}
