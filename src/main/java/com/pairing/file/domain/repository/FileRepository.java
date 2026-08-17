package com.pairing.file.domain.repository;

import com.pairing.file.domain.model.UploadedFile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileRepository {

    UploadedFile save(UploadedFile file);

    Optional<UploadedFile> findById(Long id);

    /**
     * 여러 fileId 를 한 번에. object key 만 필요한 도메인이 파일 개수만큼 되묻지 않게 한다.
     * 없는 id 는 결과에서 빠진다.
     */
    List<UploadedFile> findByIdIn(Collection<Long> ids);

    void deleteById(Long id);
}
