package com.pairing.project.infrastructure.mapper;

import com.pairing.meta.domain.model.SkillCode;
import com.pairing.project.domain.model.Position;
import com.pairing.project.domain.model.Project;
import com.pairing.project.infrastructure.persistence.PositionSkillJpaEntity;
import com.pairing.project.infrastructure.persistence.ProjectFileJpaEntity;
import com.pairing.project.infrastructure.persistence.ProjectJpaEntity;
import com.pairing.project.infrastructure.persistence.ProjectPositionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 프로젝트 애그리거트 <-> JPA 매핑.
 *
 * <p>도메인 생성자가 닫혀 있어 reconstitute 정적 팩토리로 복원한다. 포지션·요구 스킬·첨부는
 * 프로젝트와 함께 매핑한다. MapStruct 를 쓰지 않는 이유는 양방향 연관을 addPosition / addSkill
 * 로 걸어야 하고 첨부의 sortOrder 가 리스트 인덱스라 자동 생성이 되지 않기 때문이다.
 * NegotiationMapper 와 같은 방식이다.
 *
 * <p>deletedAt 은 항상 null 로 넣는다. 삭제된 프로젝트는 조회 단계에서 걸러지므로
 * 여기까지 오지 않는다.
 */
@Component
public class ProjectMapper {

    public ProjectJpaEntity toJpaEntity(Project domain) {
        if (domain == null) {
            return null;
        }

        ProjectJpaEntity entity = new ProjectJpaEntity(
                domain.getId(),
                domain.getClientId(),
                domain.getTitle(),
                domain.getStartDesiredDate(),
                domain.isStartNegotiable(),
                domain.getPeriodValue(),
                domain.getPeriodUnit(),
                domain.getBudgetAmount(),
                domain.getWorkStyle(),
                domain.getWorkForm(),
                domain.getWorkLocation(),
                domain.getCurrentSituation(),
                domain.getMainTask(),
                domain.getDetailScope(),
                domain.getExtraNote(),
                domain.getStatus(),
                domain.getPaymentStatus(),
                domain.getTotalHeadcount(),
                domain.getConfirmedHeadcount(),
                domain.getRecruitStartedAt(),
                domain.getRecruitDeadline(),
                domain.getExtensionCount(),
                domain.getFreeRerecommendUsed(),
                domain.getPaidRerecommendUsed(),
                domain.getNoticeAgreedAt(),
                domain.getCanceledAt(),
                domain.getClosedAt(),
                domain.getRetentionUntil(),
                domain.getCreatedAt(),
                null);

        for (Position position : domain.getPositions()) {
            entity.addPosition(toPositionJpaEntity(position));
        }

        // sortOrder 는 리스트 순서다. 화면 노출 순서를 그대로 유지한다.
        List<Long> fileIds = domain.getFileIds();
        for (int i = 0; i < fileIds.size(); i++) {
            entity.addFile(new ProjectFileJpaEntity(null, fileIds.get(i), i));
        }

        return entity;
    }

    /**
     * 영속 엔티티에 상태 전이 결과만 덮어쓴다.
     *
     * <p>등록은 {@link #toJpaEntity} 로 새 그래프를 만들지만, 상태 전이는 그 방식을 쓸 수 없다.
     * 자식 컬렉션이 detached 로 들어가면서 재삽입되기 때문이다.
     */
    public void applyState(ProjectJpaEntity entity, Project domain) {
        entity.applyState(
                domain.getStatus(),
                domain.getPaymentStatus(),
                domain.getTotalHeadcount(),
                domain.getConfirmedHeadcount(),
                domain.getRecruitStartedAt(),
                domain.getRecruitDeadline(),
                domain.getExtensionCount(),
                domain.getFreeRerecommendUsed(),
                domain.getPaidRerecommendUsed(),
                domain.getCanceledAt(),
                domain.getClosedAt(),
                domain.getRetentionUntil());
    }

    public Project toDomain(ProjectJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        List<Position> positions = new ArrayList<>();
        for (ProjectPositionJpaEntity position : entity.getPositions()) {
            positions.add(toPositionDomain(position));
        }

        List<Long> fileIds = new ArrayList<>();
        for (ProjectFileJpaEntity file : entity.getFiles()) {
            fileIds.add(file.getFileId());
        }

        return Project.reconstitute(
                entity.getId(),
                entity.getClientId(),
                entity.getTitle(),
                entity.getStartDesiredDate(),
                entity.isStartNegotiable(),
                entity.getPeriodValue(),
                entity.getPeriodUnit(),
                entity.getBudgetAmount(),
                entity.getWorkStyle(),
                entity.getWorkForm(),
                entity.getWorkLocation(),
                entity.getCurrentSituation(),
                entity.getMainTask(),
                entity.getDetailScope(),
                entity.getExtraNote(),
                entity.getStatus(),
                entity.getPaymentStatus(),
                entity.getTotalHeadcount(),
                entity.getConfirmedHeadcount(),
                entity.getRecruitStartedAt(),
                entity.getRecruitDeadline(),
                entity.getExtensionCount(),
                entity.getFreeRerecommendUsed(),
                entity.getPaidRerecommendUsed(),
                entity.getNoticeAgreedAt(),
                entity.getCanceledAt(),
                entity.getClosedAt(),
                entity.getRetentionUntil(),
                entity.getCreatedAt(),
                positions,
                fileIds);
    }

    /** 포지션 단건. 매칭이 projectId 없이 모집 인원만 조회할 때 쓴다. */
    public Position toPositionDomain(ProjectPositionJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        List<SkillCode> skills = new ArrayList<>();
        for (PositionSkillJpaEntity skill : entity.getSkills()) {
            skills.add(skill.getSkillCode());
        }

        return Position.reconstitute(
                entity.getId(),
                entity.getPositionNo(),
                entity.getJobCategory(),
                entity.getJobRole(),
                entity.getMinCareerYears(),
                entity.getHeadcount(),
                entity.getConfirmedCount(),
                entity.getStatus(),
                entity.getClosedAt(),
                skills);
    }

    private ProjectPositionJpaEntity toPositionJpaEntity(Position domain) {
        ProjectPositionJpaEntity entity = new ProjectPositionJpaEntity(
                domain.getId(),
                domain.getPositionNo(),
                domain.getJobCategory(),
                domain.getJobRole(),
                domain.getMinCareerYears(),
                domain.getHeadcount(),
                domain.getConfirmedCount(),
                domain.getStatus(),
                domain.getClosedAt());

        for (SkillCode skill : domain.getSkills()) {
            entity.addSkill(new PositionSkillJpaEntity(null, skill));
        }

        return entity;
    }
}
