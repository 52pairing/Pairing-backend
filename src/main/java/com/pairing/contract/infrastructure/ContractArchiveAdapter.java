package com.pairing.contract.infrastructure;

import com.pairing.contract.application.port.ContractArchivePort;
import com.pairing.file.application.command.UploadGeneratedFileCommand;
import com.pairing.file.application.usecase.FileCommandUseCase;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.file.domain.model.FilePurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * ContractArchivePort 구현. file 도메인의 인바운드 포트를 호출한다.
 *
 * <p>계약서는 사용자가 올리는 파일이 아니라 서버가 만든 문서라 {@code uploadGenerated} 를 쓴다.
 * 일반 업로드 경로는 {@code MultipartFile} 전제라 여기 맞지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ContractArchiveAdapter implements ContractArchivePort {

    private static final String CONTENT_TYPE = "application/pdf";

    private final FileCommandUseCase fileCommandUseCase;
    private final FileQueryUseCase fileQueryUseCase;

    @Override
    public Long archive(byte[] pdf, String contractNo, Long ownerAccountId) {
        return fileCommandUseCase.uploadGenerated(new UploadGeneratedFileCommand(
                ownerAccountId,
                FilePurpose.CONTRACT,
                pdf,
                "계약서_%s.pdf".formatted(contractNo),
                CONTENT_TYPE)).fileId();
    }

    @Override
    public Optional<byte[]> read(Long fileId) {
        return fileId == null ? Optional.empty() : fileQueryUseCase.readContent(fileId);
    }
}
