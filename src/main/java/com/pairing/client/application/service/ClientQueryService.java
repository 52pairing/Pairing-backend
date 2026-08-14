package com.pairing.client.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.client.application.result.ClientMyPageResult;
import com.pairing.client.application.usecase.ClientQueryUseCase;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.review.application.result.ReviewSummaryResult;
import com.pairing.review.application.usecase.ReviewUseCase;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClientQueryService implements ClientQueryUseCase {

    private final AccountQueryUseCase accountQueryUseCase;
    private final FileQueryUseCase fileQueryUseCase;
    private final ReviewUseCase reviewUseCase;
    private final SettlementQueryUseCase settlementQueryUseCase;

    @Override
    public ClientMyPageResult findMyPage(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);
        ClientProfile profile = accountQueryUseCase.getClientProfile(accountId);
        ReviewSummaryResult reviewSummary = reviewUseCase.getSummary(accountId);

        return new ClientMyPageResult(
                account.getId(),
                fileQueryUseCase.findObjectKey(profile.getLogoFileId()).orElse(null),
                profile.getCompanyName(),
                profile.getBusinessNo(),
                profile.getBusinessField(),
                profile.getEmployeeCount(),
                account.getEmail(),
                account.getName(),
                account.getPhone(),
                profile.getAddress(),
                profile.getAddressParts(),
                ClientGrade.of(profile.getGrade()),
                reviewSummary.averageScore(),
                reviewSummary.reviewCount(),
                // 진행 중 프로젝트 확인은 project 도메인에 판정 포트가 생기면 함께 본다.
                !settlementQueryUseCase.hasUnpaidSettlement(accountId)
        );
    }
}
