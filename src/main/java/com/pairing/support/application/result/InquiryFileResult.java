package com.pairing.support.application.result;

public record InquiryFileResult(
        Long fileId,
        String originalName,
        String objectKey
) {
}
