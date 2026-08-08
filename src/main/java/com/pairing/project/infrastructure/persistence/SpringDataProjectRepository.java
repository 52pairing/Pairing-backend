package com.pairing.project.infrastructure.persistence;

import com.pairing.project.domain.model.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 프로젝트 JPA 리포지토리.
 *
 * <p>컬렉션(positions / files / skills)은 JOIN FETCH 하지 않는다.
 * 컬렉션 fetch 는 DB 페이징을 인메모리로 떨어뜨리고, List 를 둘 이상 fetch 하면
 * MultipleBagFetchException 이 난다. 엔티티의 {@code @BatchSize} 가 N+1 을 막는다.
 *
 * <p>모든 조회는 삭제된 프로젝트를 제외한다.
 */
public interface SpringDataProjectRepository extends JpaRepository<ProjectJpaEntity, Long> {

    Optional<ProjectJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    /** 소유 확인·등급 조회용. 애그리거트를 로드하지 않고 client_id 만 뽑는다. */
    @Query("SELECT p.clientId FROM ProjectJpaEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Long> findClientIdById(@Param("id") Long id);

    /** 상태 검사용. 애그리거트를 로드하지 않고 status 만 뽑는다. */
    @Query("SELECT p.status FROM ProjectJpaEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProjectStatus> findStatusById(@Param("id") Long id);

    /** 계정이 소유한 프로젝트 ID 전체. 상태 무관. */
    @Query("SELECT p.id FROM ProjectJpaEntity p WHERE p.clientId = :clientId AND p.deletedAt IS NULL")
    List<Long> findIdsByClientId(@Param("clientId") Long clientId);

    Page<ProjectJpaEntity> findByClientIdAndDeletedAtIsNull(Long clientId, Pageable pageable);

    Page<ProjectJpaEntity> findByClientIdAndStatusInAndDeletedAtIsNull(
            Long clientId, List<ProjectStatus> statuses, Pageable pageable);

    /** 탭 배지용. [status, count] 배열로 돌아온다. 건수가 0인 상태는 결과에 없다. */
    @Query("SELECT p.status, COUNT(p) FROM ProjectJpaEntity p "
            + "WHERE p.clientId = :clientId AND p.deletedAt IS NULL GROUP BY p.status")
    List<Object[]> countGroupByStatus(@Param("clientId") Long clientId);

    /** 관리자 목록. status·keyword 가 null 이면 해당 조건을 건너뛴다. */
    @Query("SELECT p FROM ProjectJpaEntity p WHERE p.deletedAt IS NULL "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%'))")
    Page<ProjectJpaEntity> findAllForAdmin(@Param("status") ProjectStatus status,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);
}