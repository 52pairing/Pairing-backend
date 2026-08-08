package com.pairing.negotiation.infrastructure.project;

import com.pairing.global.exception.BusinessException;
import com.pairing.negotiation.application.port.out.ProjectReaderPort;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import com.pairing.project.domain.model.Project;
import com.pairing.project.exception.ProjectErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
}
