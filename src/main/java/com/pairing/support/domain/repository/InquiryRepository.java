package com.pairing.support.domain.repository;

import com.pairing.support.domain.model.Inquiry;
import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(Long id);

    Page<Inquiry> findByWriterAccountId(Long writerAccountId, Pageable pageable);

    Page<Inquiry> findByWriterAccountIdAndStatus(Long writerAccountId, InquiryStatus status, Pageable pageable);
}
