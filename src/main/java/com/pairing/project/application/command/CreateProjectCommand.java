package com.pairing.project.application.command;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.domain.model.PositionUpdate;

import java.time.LocalDate;
import java.util.List;

/**
 * 프로젝트 등록 커맨드.
 *
 * <p>accountId 는 로그인 계정이다. client_profile.id 변환은 서비스가 한다.
 * 근무 장소는 등록 폼에서 받지 않고 상주일 때 클라이언트 주소를 복사하므로 여기에 없다.
 */
public record CreateProjectCommand(
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