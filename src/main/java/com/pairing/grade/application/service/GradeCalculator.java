package com.pairing.grade.application.service;

import com.pairing.contract.application.usecase.ContractQueryUseCase;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.global.exception.BusinessException;
import com.pairing.grade.application.result.GradeSnapshot;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.ProjectStatus;
import com.pairing.review.application.usecase.ReviewUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 등급 판정에 쓰는 실적을 모은다.
 *
 * <p>마이페이지 승급 안내와 월간 산정 배치가 <b>이 클래스 하나만</b> 쓴다. 각자 계산하면
 * "조건을 충족했습니다"라고 안내한 다음 달에 승급이 안 되는 일이 생긴다.
 */
@Component
@RequiredArgsConstructor
public class GradeCalculator {

    /**
     * 계약을 훑는 최대 건수.
     *
     * <p>가장 높은 승급 조건이 20건이라 판정에는 충분하다. 화면에 현재 건수를 그대로 보여주므로
     * 조건보다 넉넉히 잡았다. 이보다 많은 계약을 가진 계정은 건수가 실제보다 적게 보일 수 있지만,
     * 이미 최고 등급 조건을 넘긴 상태라 <b>등급 판정은 어긋나지 않는다.</b>
     */
    private static final int SCAN_LIMIT = 200;

    private final ReviewUseCase reviewUseCase;
    private final ContractQueryUseCase contractQueryUseCase;
    private final ProjectQueryUseCase projectQueryUseCase;

    public GradeSnapshot snapshot(Long accountId) {
        List<Contract> settled = contractQueryUseCase
                .findMine(accountId, null, null, PageRequest.of(0, SCAN_LIMIT))
                .getContent().stream()
                .map(summary -> summary.contract())
                .filter(this::isSettled)
                .toList();

        LocalDateTime lastCompletedAt = settled.stream()
                .map(Contract::getCompletedAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new GradeSnapshot(
                reviewUseCase.getSummary(accountId).averageScore(),
                settled.size(),
                lastCompletedAt);
    }

    /**
     * 대금 지급까지 끝난 계약인지. 승급 조건의 "완료 건수"가 이 기준이다.
     *
     * <p>성공보수 수수료까지 결제되어 프로젝트가 CLOSED 가 된 건만 센다. 계약의 COMPLETED 는
     * 검수 완료 시점이라 성공보수 결제 전이어서 쓰지 않는다. 파기·거부된 계약은 실적이 아니다.
     *
     * <p><b>리뷰 작성 조건과 완전히 같지는 않다.</b> 리뷰는 "본인이 성공보수를 냈는지"까지 보는데
     * 여기서는 보지 않는다. 등급은 거래를 끝냈다는 실적이고 수수료 납부는 그 뒤의 정산 문제라
     * 지금은 나눠 뒀다.
     */
    private boolean isSettled(Contract contract) {
        if (contract.getStatus() != ContractStatus.SIGNED && contract.getStatus() != ContractStatus.COMPLETED) {
            return false;
        }
        try {
            return projectQueryUseCase.getById(contract.getProjectId()).getStatus() == ProjectStatus.CLOSED;
        } catch (BusinessException e) {
            // 프로젝트가 지워졌으면 판단할 근거가 없다. 실적으로 세지 않는다.
            return false;
        }
    }
}
