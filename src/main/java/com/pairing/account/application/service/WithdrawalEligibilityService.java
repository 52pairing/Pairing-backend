package com.pairing.account.application.service;

import com.pairing.account.application.result.WithdrawalEligibilityResult;
import com.pairing.account.application.result.WithdrawalEligibilityResult.Blocked;
import com.pairing.account.application.usecase.AccountQueryUseCase;
import com.pairing.account.application.usecase.WithdrawalEligibilityUseCase;
import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.WithdrawalBlocker;
import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 탈퇴를 막는 사유를 모아 준다. (R31)
 *
 * <p>사유별로 건수를 세는 이유는 화면이 "진행 중인 협상이 1건" 처럼 안내해서다. 하나 걸렸다고
 * 그만두지 않고 끝까지 세야 사용자가 무엇을 정리해야 하는지 한 번에 안다.
 *
 * <p>역할로 보는 대상이 다르다. 클라이언트는 자기가 등록한 <b>프로젝트</b>, 프리랜서는 자기가 맺은
 * <b>계약</b>이다. 한쪽만 보면 반대쪽이 뚫린다.
 *
 * <p>사용자가 남긴 기록(완료된 프로젝트·리뷰·협상 채팅)은 탈퇴를 막지 않는다. 여기서 보는 건
 * 아직 끝나지 않아 상대방이 기다리고 있는 일뿐이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WithdrawalEligibilityService implements WithdrawalEligibilityUseCase {

    /** 프로젝트가 이 상태면 더 진행할 게 없다. */
    private static final Set<ProjectStatus> CLOSED_PROJECT_STATUSES =
            Set.of(ProjectStatus.CLOSED, ProjectStatus.CANCELED);

    /** 계약이 이 상태면 더 진행할 게 없다. */
    private static final Set<ContractStatus> CLOSED_CONTRACT_STATUSES =
            Set.of(ContractStatus.COMPLETED, ContractStatus.REJECTED, ContractStatus.TERMINATED);

    /**
     * 진행 중 계약을 셀 때 훑는 최대 건수.
     *
     * <p>이보다 많은 계약을 가진 계정은 뒤쪽을 못 센다. 그런 계정은 앞쪽에서 이미 진행 중 계약이
     * 잡혀 탈퇴가 막히므로 <b>가능/불가 판정은 어긋나지 않고</b>, 화면에 보이는 건수만 실제보다 적을 수 있다.
     */
    private static final int ONGOING_SCAN_LIMIT = 200;

    private final AccountQueryUseCase accountQueryUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;
    private final ContractQueryUseCase contractQueryUseCase;
    private final SettlementQueryUseCase settlementQueryUseCase;

    @Override
    public WithdrawalEligibilityResult getWithdrawalEligibility(Long accountId) {
        Account account = accountQueryUseCase.getById(accountId);

        List<Blocked> blockers = new ArrayList<>(account.hasRole(Role.CLIENT)
                ? projectBlockers(accountId)
                : contractBlockers(accountId));

        if (settlementQueryUseCase.hasUnpaidSettlement(accountId)) {
            // 미납은 건수를 세지 않는다. "몇 건인지"보다 "결제 화면으로 가라"가 필요한 정보다.
            blockers.add(new Blocked(WithdrawalBlocker.UNPAID_SETTLEMENT, 1));
        }
        return WithdrawalEligibilityResult.of(blockers);
    }

    /** 클라이언트: 아직 끝나지 않은 프로젝트. 협상 단계는 따로 센다 — 화면이 구분해 보여준다. */
    private List<Blocked> projectBlockers(Long accountId) {
        Map<ProjectStatus, Long> byStatus = projectQueryUseCase.findProjectIdsByAccountId(accountId).stream()
                .map(projectQueryUseCase::findStatus)
                .filter(status -> !CLOSED_PROJECT_STATUSES.contains(status))
                .collect(Collectors.groupingBy(status -> status, Collectors.counting()));

        long negotiating = byStatus.getOrDefault(ProjectStatus.NEGOTIATING, 0L);
        long others = total(byStatus) - negotiating;

        return toBlockers(
                new Blocked(WithdrawalBlocker.NEGOTIATION, negotiating),
                new Blocked(WithdrawalBlocker.PROJECT, others));
    }

    /** 프리랜서: 아직 끝나지 않은 계약. 서명 대기는 따로 센다. */
    private List<Blocked> contractBlockers(Long accountId) {
        Map<ContractStatus, Long> byStatus = contractQueryUseCase
                .findMine(accountId, null, null, PageRequest.of(0, ONGOING_SCAN_LIMIT))
                .getContent().stream()
                .map(summary -> summary.contract().getStatus())
                .filter(status -> !CLOSED_CONTRACT_STATUSES.contains(status))
                .collect(Collectors.groupingBy(status -> status, Collectors.counting()));

        long signPending = byStatus.getOrDefault(ContractStatus.SIGN_PENDING, 0L);
        long others = total(byStatus) - signPending;

        return toBlockers(
                new Blocked(WithdrawalBlocker.SIGN_PENDING_CONTRACT, signPending),
                new Blocked(WithdrawalBlocker.CONTRACT, others));
    }

    private long total(Map<?, Long> byStatus) {
        return byStatus.values().stream().mapToLong(Long::longValue).sum();
    }

    /** 0건인 사유는 내리지 않는다. 화면에 "진행 중인 계약 0건" 이 뜨면 안 된다. */
    private List<Blocked> toBlockers(Blocked... candidates) {
        return Arrays.stream(candidates)
                .filter(blocked -> blocked.count() > 0)
                .toList();
    }
}
