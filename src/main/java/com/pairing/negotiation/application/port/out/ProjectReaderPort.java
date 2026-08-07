package com.pairing.negotiation.application.port.out;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.util.Optional;

/**
 * project 도메인 읽기 포트(협상 소유). project 기능은 아직 스켈레톤이라 협상이 project 테이블을
 * 읽기 전용으로 직접 조회한다. 나중에 project 도메인이 조회 포트를 제공하면 어댑터만 교체한다.
 *
 * <p>조회 role 판정(clientProfileId)·화면 표시(title)와, 생성 diff 의 클라 희망값
 * (예산/근무방식/근무형태/착수일)을 함께 제공한다.
 */
public interface ProjectReaderPort {

    Optional<ProjectView> findById(Long projectId);

    /** 협상에 필요한 project 최소 조회 모델. */
    record ProjectView(
            Long projectId,
            Long clientProfileId,   // project.client_id = client_profile.id
            String title,
            Long budgetAmount,      // 등록 예산(원, 공개 희망값 표시용)
            WorkStyle workStyle,
            WorkForm workForm,
            LocalDate startDesiredDate,
            boolean startNegotiable,
            Integer periodValue,
            PeriodUnit periodUnit
    ) {
    }
}
