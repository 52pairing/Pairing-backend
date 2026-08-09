package com.pairing.support.application.command;

import java.util.List;

public record CreateInquiryCommand(
        Long writerAccountId,
        String title,
        String content,
        List<Long> fileIds
) {
}
