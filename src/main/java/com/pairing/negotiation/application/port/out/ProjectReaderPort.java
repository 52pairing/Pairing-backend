package com.pairing.negotiation.application.port.out;

import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * 협상 목록 카드용 최소 정보(제목·클라 프로필 ID)를 여러 프로젝트에 대해 한 번에 읽는다.
     *
     * <p>목록은 행마다 {@link #findById} 를 부르면 페이지 크기만큼 쿼리가 나가는 N+1 이 된다.
     * 카드가 실제로 쓰는 필드만 IN 절 한 번으로 모아 그 반복을 없앤다. 상세({@link ProjectView})의
     * 나머지 필드는 목록에 필요 없어 싣지 않는다. 없는 ID 는 맵에서 빠진다(부재 허용).
     */
    Map<Long, ProjectCardInfo> findCardInfoByIds(Collection<Long> projectIds);

    /** 협상 목록 카드가 프로젝트에서 쓰는 값만. */
    record ProjectCardInfo(Long clientProfileId, String title) {
    }

    /**
     * 내가 소유한 프로젝트 ID 목록. 협상은 clientProfileId 를 갖지 않아, 클라 기준으로 협상을 셀 때
     * 이 목록으로 좁힌다(헤더 응답대기 배지). 소유 프로젝트가 없으면 빈 목록.
     */
    List<Long> findMyProjectIds(Long accountId);

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
