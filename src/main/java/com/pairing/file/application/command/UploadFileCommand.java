package com.pairing.file.application.command;

import com.pairing.file.domain.model.FilePurpose;
import org.springframework.web.multipart.MultipartFile;

public record UploadFileCommand(
        Long accountId,
        FilePurpose purpose,
        MultipartFile file
) {
}
