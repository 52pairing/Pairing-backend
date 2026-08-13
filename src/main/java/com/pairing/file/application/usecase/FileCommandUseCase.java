package com.pairing.file.application.usecase;

import com.pairing.file.application.command.UploadFileCommand;
import com.pairing.file.application.command.UploadGeneratedFileCommand;
import com.pairing.file.application.result.FileResult;

public interface FileCommandUseCase {

    /** 용도별 크기/확장자 제한을 넘으면 {@code FI_003} / {@code GLOBAL_008}. */
    FileResult upload(UploadFileCommand command);

    /**
     * 서버가 만든 바이트를 저장한다. 계약서 PDF 처럼 사용자가 올리지 않는 파일에 쓴다.
     *
     * <p>확장자 검증은 하지 않는다. 서버가 만든 것이라 내용과 MIME 을 이미 알고 있고, 검증할
     * 원본 파일명이 없다. 크기 상한은 그대로 본다.
     */
    FileResult uploadGenerated(UploadGeneratedFileCommand command);

    /** 업로더 본인이 아니면 {@code FI_002}. */
    void delete(Long fileId, Long accountId);
}
