package com.pairing.file.application.usecase;

import com.pairing.file.application.command.UploadFileCommand;
import com.pairing.file.application.result.FileResult;

public interface FileCommandUseCase {

    /** 용도별 크기/확장자 제한을 넘으면 {@code FI_003} / {@code GLOBAL_008}. */
    FileResult upload(UploadFileCommand command);

    /** 업로더 본인이 아니면 {@code FI_002}. */
    void delete(Long fileId, Long accountId);
}
