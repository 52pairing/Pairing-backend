package com.pairing.contract.application.port;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.util.List;

/**
 * 계약이 필요한 프로젝트 값. 계약서 머리말과 목록 카드에 쓴다.
 *
 * <p>계약은 포지션 1자리당 1건이라 {@code positionId} 로 물으면 프로젝트명과 직무가 함께 나온다.
 */
public interface ContractProjectReaderPort {

    /** 포지션이 속한 프로젝트의 이름과 직무. 없으면 null 필드로 채워진 뷰. */
    ProjectView findByPositionId(Long positionId);

    /**
     * 계약서에 박을 프로젝트 조건. 협상에서 합의되지 않은 항목의 기본값으로 쓴다.
     *
     * <p>협상은 서로 맞지 않는 항목만 다루므로, 합의 조건에 없는 값은 프로젝트 등록값이 그대로 간다.
     * 없으면 PJ_001.
     */
    ProjectContractView findForContract(Long projectId);

    /** {@code skills} 는 그 포지션의 요구 기술이다. 계약서 제2조에 그대로 나열한다. */
    record ProjectView(String projectTitle, JobRole jobRole, List<SkillCode> skills) {

        /** 프로젝트나 포지션이 지워졌을 때. 계약은 5년 보관이라 원본보다 오래 남는다. */
        public static final ProjectView EMPTY = new ProjectView(null, null, List.of());
    }

    /**
     * {@code clientProfileId} 는 계약의 갑이다. {@code account.id} 가 아니다.
     *
     * <p>{@code mainTask}/{@code detailScope} 는 계약서 제2조에 들어갈 업무 원문이다. 최대 1500자라
     * 그대로 넣을 수 없어 AI 서버가 줄여 준다. 등록 시 선택 입력이라 둘 다 비어 있을 수 있다.
     */
    record ProjectContractView(
            Long clientProfileId,
            WorkStyle workStyle,
            WorkForm workForm,
            String workLocation,
            LocalDate startDesiredDate,
            int periodValue,
            PeriodUnit periodUnit,
            String mainTask,
            String detailScope
    ) {
    }
}
