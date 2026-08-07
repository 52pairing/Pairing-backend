package com.pairing.file.application.result;

import com.pairing.file.domain.model.UploadedFile;

/** {@code objectKey} 는 상대경로다. 다른 도메인은 이 값을 자기 응답 DTO의 {@code ~Url} 필드에 그대로 담으면 된다. */
public record FileResult(
        Long fileId,
        String originalName,
        String objectKey,
        String mimeType,
        Long sizeBytes
) {
    public static FileResult from(UploadedFile file) {
        return new FileResult(file.getId(), file.getOriginalName(), file.getObjectKey(), file.getMimeType(),
                file.getSizeBytes());
    }
}
