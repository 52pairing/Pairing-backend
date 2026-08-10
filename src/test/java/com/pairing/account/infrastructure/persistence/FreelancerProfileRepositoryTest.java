package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.FreelancerProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로젝트 사전 검수(정책 P02)가 후보 수를 셀 때 쓰는 {@code filterActiveAiMatchingAgreed}가
 * 매칭 일시중지(matchingPaused)까지 실제로 걸러내는지 확인한다.
 *
 * <p>매칭 하드필터(Stage B, Python)에 매칭 일시중지 조건을 추가하면서, 사전 검수가 안내하는
 * 후보 수와 실제 추천 후보 집합이 다시 어긋나지 않도록 이 카운트 쿼리도 같이 맞췄다.
 */
@SpringBootTest
@Transactional
class FreelancerProfileRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private FreelancerProfileRepository freelancerProfileRepository;

    private Long createFreelancer(String email, String phone, boolean aiMatchingAgreed, boolean matchingPaused) {
        Long accountId = accountRepository.save(Account.createByEmail(
                email, "$2a$10$hash", Role.FREELANCER, "홍길동", phone)).getId();
        FreelancerProfile profile = FreelancerProfile.create(accountId, LocalDate.of(1995, 3, 1));
        profile.updateMatchingSettings(aiMatchingAgreed, matchingPaused);
        freelancerProfileRepository.save(profile);
        return accountId;
    }

    @Test
    @DisplayName("AI매칭 동의 + 매칭 미중지인 계정만 후보로 남고, 매칭 중지·동의 미비 계정은 빠진다")
    void excludesMatchingPausedAndNotAgreedAccounts() {
        Long eligible = createFreelancer("p02-eligible@pairing.com", "01011110001", true, false);
        Long paused = createFreelancer("p02-paused@pairing.com", "01011110002", true, true);
        Long notAgreed = createFreelancer("p02-not-agreed@pairing.com", "01011110003", false, false);

        List<Long> result = freelancerProfileRepository.filterActiveAiMatchingAgreed(
                List.of(eligible, paused, notAgreed));

        assertThat(result).containsExactly(eligible);
    }
}
