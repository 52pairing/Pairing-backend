package com.pairing.grade.application.service;

import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.grade.application.result.GradeSnapshot;
import com.pairing.grade.application.result.MyGradeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** 마이페이지 "다음 등급까지" 문구: 기준값을 현재 등급 칸에서 읽는지. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeQueryServiceTest {

    private static final Long ACCOUNT_ID = 42L;

    @Mock
    private AccountQueryUseCase accountQueryUseCase;
    @Mock
    private GradeCalculator gradeCalculator;
    @Mock
    private Account account;
    @Mock
    private ClientProfile clientProfile;

    @InjectMocks
    private GradeQueryService gradeQueryService;

    private void givenClient(ClientGrade grade, Double ratingAverage, int completedCount) {
        given(account.getRole()).willReturn(Role.CLIENT);
        given(accountQueryUseCase.getById(ACCOUNT_ID)).willReturn(account);
        given(accountQueryUseCase.getClientProfile(ACCOUNT_ID)).willReturn(clientProfile);
        given(clientProfile.getGrade()).willReturn(grade.name());
        given(gradeCalculator.snapshot(ACCOUNT_ID))
                .willReturn(new GradeSnapshot(ratingAverage, completedCount, null));
    }

    @Test
    @DisplayName("최고 등급 바로 아래(골드)도 500 없이 조회되고, 다이아 조건을 안내한다")
    void guidesSecondHighestTierWithoutError() {
        givenClient(ClientGrade.GOLD, null, 12);

        MyGradeResult result = gradeQueryService.getMyGrade(ACCOUNT_ID);

        assertThat(result.grade()).isEqualTo("GOLD");
        assertThat(result.nextGrade()).isEqualTo("DIAMOND");
        // 골드 칸의 기준값(별점 4.0 / 완료 20건)으로 안내한다.
        assertThat(result.nextGradeGuide())
                .contains("아직 리뷰가 없어")
                .contains("8건 더 필요");
    }

    @Test
    @DisplayName("최고 등급(다이아)은 다음 등급도 안내 문구도 없다")
    void topTierHasNoNextGrade() {
        givenClient(ClientGrade.DIAMOND, 5.0, 30);

        MyGradeResult result = gradeQueryService.getMyGrade(ACCOUNT_ID);

        assertThat(result.grade()).isEqualTo("DIAMOND");
        assertThat(result.nextGrade()).isNull();
        assertThat(result.nextGradeGuide()).isNull();
    }

    @Test
    @DisplayName("실버의 승급 안내는 골드 기준(별점 3.0 / 완료 10건)이다")
    void guidesBaseTierWithItsOwnThresholds() {
        givenClient(ClientGrade.SILVER, 2.5, 4);

        MyGradeResult result = gradeQueryService.getMyGrade(ACCOUNT_ID);

        assertThat(result.nextGrade()).isEqualTo("GOLD");
        assertThat(result.nextGradeGuide())
                .contains("3.0점 이상")
                .contains("6건 더 필요");
    }
}
