package com.pairing.file.application.usecase;

import com.pairing.file.application.result.FileResult;

import java.util.Optional;

public interface FileQueryUseCase {

    /** 없으면 {@code FI_001}. */
    FileResult getById(Long fileId);

    /**
     * 다른 도메인이 자기 응답을 조립할 때 fileId 로 object key 만 가져오는 용도.
     * fileId 가 null 이거나 대상이 없으면 empty (다른 도메인은 이걸 profileImageUrl 등에 null 로 반영하면 된다).
     */
    Optional<String> findObjectKey(Long fileId);
}
