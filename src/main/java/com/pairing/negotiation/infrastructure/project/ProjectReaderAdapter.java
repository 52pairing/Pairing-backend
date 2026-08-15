package com.pairing.negotiation.infrastructure.project;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * project 도메인 읽기 어댑터. 협상은 자체 포트({@link ProjectReaderPort}·{@code ProjectView})만 알고,
 * 실제 조회는 project 도메인의 인바운드 포트({@link ProjectQueryUseCase})에 위임한다.
 *
 * <p>예전엔 협상이 {@code project} 테이블을 읽기 전용 엔티티로 직접 매핑했으나, project 도메인이
 * 정식 엔티티/조회 포트를 제공하면서 <b>같은 테이블을 두 엔티티가 매핑하는 문제</b>가 생겼다.
 * 그 읽기 엔티티를 제거하고 이 어댑터가 포트로만 조회하도록 바꿨다(포트 javadoc 예고대로 "어댑터만 교체").
 *
 * <p>{@code getById} 는 대상이 없으면 {@code PROJECT_NOT_FOUND} 예외를 던진다. 협상 조회는
 * 프로젝트 부재를 허용(빈 값)하므로 그 예외만 {@link Optional#empty()} 로 흡수하고, 다른 예외는 전파한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectReaderAdapter implements ProjectReaderPort {

    private final ProjectQueryUseCase projectQueryUseCase;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ProjectView> findById(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        try {
            Project p = projectQueryUseCase.getById(projectId);
            return Optional.of(new ProjectView(
                    p.getId(), p.getClientId(), p.getTitle(),
                    p.getBudgetAmount(), p.getWorkStyle(), p.getWorkForm(),
                    p.getStartDesiredDate(), p.isStartNegotiable(),
                    p.getPeriodValue(), p.getPeriodUnit()));
        } catch (BusinessException e) {
            if (e.getErrorCode() == ProjectErrorCode.PROJECT_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * 목록 카드용 최소 컬럼만 IN 절 한 번으로 읽는다. 소유 도메인(account 이름)과 같은 방식으로
     * 읽기 전용 스칼라만 가져와, 상세 엔티티({@link Project}) 로딩이 유발할 수 있는 2차 쿼리를 피한다.
     * {@code findById} 와 동일하게 소프트 삭제된 프로젝트는 제외한다(카드 제목이 비게 됨).
     */
    @Override
    public Map<Long, ProjectCardInfo> findCardInfoByIds(Collection<Long> projectIds) {
        List<Long> ids = projectIds == null ? List.of()
                : projectIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT id, client_id, title FROM project "
                + "WHERE deleted_at IS NULL AND id IN (" + placeholders + ")";

        Map<Long, ProjectCardInfo> result = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            result.put(rs.getLong("id"),
                    new ProjectCardInfo(
                            rs.getObject("client_id", Long.class),
                            rs.getString("title")));
        }, ids.toArray());
        return result;
    }

    @Override
    public List<Long> findMyProjectIds(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        return projectQueryUseCase.findProjectIdsByAccountId(accountId);
    }
}
