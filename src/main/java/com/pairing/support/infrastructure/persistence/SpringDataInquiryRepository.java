package com.pairing.support.infrastructure.persistence;

import com.pairing.account.domain.model.Role;
import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface SpringDataInquiryRepository extends JpaRepository<InquiryJpaEntity, Long> {

    Page<InquiryJpaEntity> findByWriterAccountId(Long writerAccountId, Pageable pageable);

    Page<InquiryJpaEntity> findByWriterAccountIdAndStatus(Long writerAccountId, InquiryStatus status,
                                                          Pageable pageable);

    @Query("SELECT i FROM InquiryJpaEntity i "
            + "WHERE (:status IS NULL OR i.status = :status) "
            + "AND (:writerRole IS NULL OR i.writerRole = :writerRole) "
            + "AND (:keyword IS NULL OR ("
            + "     LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "  OR LOWER(i.writerName) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "  OR (:keywordDigits IS NOT NULL AND CAST(i.id AS string) LIKE CONCAT('%', :keywordDigits, '%'))"
            + "))")
    Page<InquiryJpaEntity> search(@Param("keyword") String keyword, @Param("keywordDigits") String keywordDigits,
                                  @Param("writerRole") Role writerRole, @Param("status") InquiryStatus status,
                                  Pageable pageable);

    long countByStatus(InquiryStatus status);

    long countByCreatedAtAfter(LocalDateTime from);
}
