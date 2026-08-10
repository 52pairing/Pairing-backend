package com.pairing.client.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.client.application.result.ClientMyPageResult;
import com.pairing.client.application.usecase.ClientQueryUseCase;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientQueryService implements ClientQueryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final ReviewUseCase reviewUseCase;

    @Override
    public ClientMyPageResult findMyPage(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);
        ClientProfile profile = accountQueryUseCase.getClientProfile(accountId);
        ReviewSummaryResult reviewSummary = reviewUseCase.getSummary(accountId);

        return new ClientMyPageResult(
                account.getId(),
                profile.getCompanyName(),
                profile.getBusinessNo(),
                profile.getBusinessField(),
                profile.getEmployeeCount(),
                account.getEmail(),
                account.getName(),
                account.getPhone(),
                profile.getAddress(),
                ClientGrade.valueOf(profile.getGrade()),
                reviewSummary.averageScore(),
                reviewSummary.reviewCount(),
                // TODO: project/settlement 도메인 구현 후 진행 중 프로젝트·미납 요금 확인
                true
        );
    }
}
