package com.pairing.file.application.service;

import com.pairing.file.application.command.UploadFileCommand;
import com.pairing.file.application.command.UploadGeneratedFileCommand;
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

    /**
     * 서버가 만든 바이트를 저장한다.
     *
     * <p>확장자 검증을 건너뛰는 이유는 원본 파일명이 없기 때문이다. 사용자 업로드는 파일명을
     * 믿을 수 없어 내용을 뜯어보지만, 여기 오는 바이트는 서버가 방금 만든 것이라 내용과 MIME 을
     * 이미 안다. 크기 상한은 그대로 본다 — 렌더링이 잘못돼 거대한 파일이 올라가는 것은 막아야 한다.
     */
    @Override
    public FileResult uploadGenerated(UploadGeneratedFileCommand command) {
        byte[] content = command.content();
        if (content == null || content.length == 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
        long maxBytes = command.purpose().getMaxSizeMb() * 1024L * 1024L;
        if (content.length > maxBytes) {
            throw new BusinessException(FileErrorCode.FILE_TOO_LARGE);
        }

        String directory = command.purpose().name().toLowerCase();
        String objectKey = fileStoragePort.uploadBytes(content, directory,
                extensionOf(command.originalName()), command.contentType());

        UploadedFile uploaded = UploadedFile.create(command.ownerAccountId(), command.purpose(), objectKey,
                command.originalName(), command.contentType(), (long) content.length);
        return FileResult.from(fileRepository.save(uploaded));
    }

    /** object key 에 붙일 확장자. 없으면 빈 문자열. */
    private String extensionOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.'));
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
            case PORTFOLIO, CONTRACT -> detected == FileType.PDF;
            case PROJECT_FILE, INQUIRY_ATTACHMENT -> detected == FileType.PDF || detected == FileType.IMAGE;
        };
    }
}
