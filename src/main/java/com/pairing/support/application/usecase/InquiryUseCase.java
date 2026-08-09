package com.pairing.support.application.usecase;

import com.pairing.support.application.command.CreateInquiryCommand;
import com.pairing.support.application.result.InquiryResult;
import com.pairing.support.domain.model.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryUseCase {

    InquiryResult create(CreateInquiryCommand command);

    Page<InquiryResult> findMine(Long accountId, InquiryStatus status, Pageable pageable);

    /** 작성자 본인 또는 관리자만 열람할 수 있다. 그 외에는 {@code IQ_002}, 없으면 {@code IQ_001}. */
    InquiryResult findOne(Long accountId, Long inquiryId);
}
