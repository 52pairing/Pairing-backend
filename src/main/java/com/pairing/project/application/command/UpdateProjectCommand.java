package com.pairing.project.application.command;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionUpdate;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 수정 입력. (요구사항 R32)
 *
 * <p>등록 시 입력한 값을 모두 담는다. 부분 수정이 아니라 전체 교체다.
 * 어떤 항목을 바꿀 수 있는지는 상태에 따라 도메인이 판정한다.
 *
 * <p>{@code positions} 의 positionId 가 있으면 기존 포지션 수정, null 이면 추가,
 * 목록에 없는 기존 포지션은 삭제다.
 */
public record UpdateProjectCommand(
        Long projectId,
        Long accountId,
        String title,
        LocalDate startDesiredDate,
        boolean startNegotiable,
        int periodValue,
        PeriodUnit periodUnit,
        Long budgetAmount,
        WorkStyle workStyle,
        WorkForm workForm,
        String currentSituation,
        String mainTask,
        String detailScope,
        String extraNote,
        List<PositionUpdate> positions,
        List<Long> fileIds
) {
}
