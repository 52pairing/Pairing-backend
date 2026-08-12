package com.pairing.support.infrastructure.persistence;

import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자가 자기 문의를 보는 조회만 남아 있다.
 *
 * <p>관리자 목록 검색(회원명·제목·문의번호)은 관리자 서버(pairing-admin)로 옮겼다.
 */
public interface SpringDataInquiryRepository extends JpaRepository<InquiryJpaEntity, Long> {

    Page<InquiryJpaEntity> findByWriterAccountId(Long writerAccountId, Pageable pageable);

    Page<InquiryJpaEntity> findByWriterAccountIdAndStatus(Long writerAccountId, InquiryStatus status,
                                                          Pageable pageable);
}
