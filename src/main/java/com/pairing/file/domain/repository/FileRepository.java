package com.pairing.file.domain.repository;

import com.pairing.file.domain.model.UploadedFile;

import java.util.Optional;

public interface FileRepository {

    UploadedFile save(UploadedFile file);

    Optional<UploadedFile> findById(Long id);

    void deleteById(Long id);
}
