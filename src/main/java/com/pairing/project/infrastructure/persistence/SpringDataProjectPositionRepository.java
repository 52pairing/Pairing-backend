package com.pairing.project.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 포지션 단건 조회용. 매칭이 projectId 없이 모집 인원만 물어볼 때 쓴다.
 *
 * <p>포지션 저장은 Project 애그리거트의 cascade 로 처리하므로 여기서 하지 않는다.
 */
public interface SpringDataProjectPositionRepository
        extends JpaRepository<ProjectPositionJpaEntity, Long> {

    Optional<ProjectPositionJpaEntity> findById(Long id);

    /** 포지션이 속한 프로젝트 ID. FK 컬럼만 읽으므로 프로젝트를 로드하지 않는다. */
    @Query("SELECT pp.project.id FROM ProjectPositionJpaEntity pp WHERE pp.id = :id")
    Optional<Long> findProjectIdById(@Param("id") Long id);
}