package com.pairing.file.application.service;

import com.pairing.file.application.command.UploadFileCommand;
import com.pairing.file.application.result.FileResult;
import com.pairing.file.application.usecase.FileCommandUseCase;
import com.pairing.file.domain.model.FilePurpose;
import com.pairing.file.domain.model.UploadedFile;
import com.pairing.file.domain.repository.FileRepository;
import com.pairing.file.exception.FileErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.port.out.FileStoragePort;
import com.pairing.global.type.FileType;
import com.pairing.global.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
@RequiredArgsConstructor
public class FileCommandService implements FileCommandUseCase {

    private final FileRepository fileRepository;
    private final FileStoragePort fileStoragePort;

    @Override
    public FileResult upload(UploadFileCommand command) {
        validate(command.purpose(), command.file());

        String directory = command.purpose().name().toLowerCase();
        String objectKey = fileStoragePort.uploadFile(command.file(), directory);

        UploadedFile uploaded = UploadedFile.create(command.accountId(), command.purpose(), objectKey,
                command.file().getOriginalFilename(), command.file().getContentType(), command.file().getSize());
        return FileResult.from(fileRepository.save(uploaded));
    }

    @Override
    public void delete(Long fileId, Long accountId) {
        UploadedFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(FileErrorCode.FILE_NOT_FOUND));
        if (!file.getOwnerAccountId().equals(accountId)) {
            throw new BusinessException(FileErrorCode.FILE_ACCESS_DENIED);
        }
        fileRepository.deleteById(fileId);
        // 스토리지 삭제가 실패해도 어댑터에서 로깅만 하고 넘어가므로 메타 삭제(본 트랜잭션)에는 영향이 없다.
        fileStoragePort.deleteFile(file.getObjectKey());
    }

    private void validate(FilePurpose purpose, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
        long maxBytes = purpose.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
        }
        if (!isAllowed(purpose, FileTypeDetector.determineFileType(file))) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
    }

    private boolean isAllowed(FilePurpose purpose, FileType detected) {
        return switch (purpose) {
            case PROFILE_IMAGE, COMPANY_LOGO, SIGNATURE -> detected == FileType.IMAGE;
            case PORTFOLIO -> detected == FileType.PDF;
            case PROJECT_FILE -> detected == FileType.PDF || detected == FileType.IMAGE;
        };
    }
}
