package com.pairing.support.application.result;

import com.pairing.account.domain.model.Role;
import com.pairing.support.domain.model.InquiryStatus;

import java.time.LocalDateTime;
import java.util.List;

/** {@code writerName}/{@code writerRole}/{@code writerEmail} 은 관리자 화면에서만 채워진다. 그 외에는 null. */
public record InquiryResult(
        Long inquiryId,
        String inquiryNo,
        Long writerAccountId,
        String writerName,
        Role writerRole,
        String writerEmail,
        String title,
        String content,
        InquiryStatus status,
        String answer,
        String answererName,
        LocalDateTime answeredAt,
        List<InquiryFileResult> files,
        LocalDateTime createdAt
) {
}
