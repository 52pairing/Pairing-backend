package com.pairing.support.domain.repository;

import com.pairing.account.domain.model.Role;
import com.pairing.support.domain.model.Inquiry;
import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(Long id);

    Page<Inquiry> findByWriterAccountId(Long writerAccountId, Pageable pageable);

    Page<Inquiry> findByWriterAccountIdAndStatus(Long writerAccountId, InquiryStatus status, Pageable pageable);

    /**
     * [관리자] 회원명·제목·문의번호 통합 검색 + 회원유형·상태 필터. 각 조건은 null 이면 걸지 않는다.
     * 계정 테이블 조인 없이 {@code Inquiry} 자체에 스냅샷된 필드만으로 처리한다.
     */
    Page<Inquiry> search(String keyword, Role writerRole, InquiryStatus status, Pageable pageable);

    long count();

    long countByStatus(InquiryStatus status);

    long countByCreatedAtAfter(LocalDateTime from);
}
