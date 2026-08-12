package com.pairing.file.application.service;

import com.pairing.file.application.result.FileResult;
import com.pairing.file.application.usecase.FileQueryUseCase;
import com.pairing.file.domain.model.UploadedFile;
import com.pairing.file.domain.repository.FileRepository;
import com.pairing.file.exception.FileErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FileQueryService implements FileQueryUseCase {

    private final FileRepository fileRepository;

    @Override
    public FileResult getById(Long fileId) {
        return fileRepository.findById(fileId)
                .map(FileResult::from)
                .orElseThrow(() -> new BusinessException(FileErrorCode.FILE_NOT_FOUND));
    }

    @Override
    public Optional<String> findObjectKey(Long fileId) {
        if (fileId == null) {
            return Optional.empty();
        }
        return fileRepository.findById(fileId).map(UploadedFile::getObjectKey);
    }

    @Override
    public boolean isOwnedBy(Long fileId, Long accountId) {
        if (fileId == null || accountId == null) {
            return false;
        }
        return fileRepository.findById(fileId)
                .map(file -> accountId.equals(file.getOwnerAccountId()))
                .orElse(false);
    }
}
