package com.pairing.file.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 공통 파일 업로드 메타. (요구사항 04. File)
 *
 * <p>DB에는 object key(상대경로)만 저장한다. 절대 URL 조립은 이 값을 담는 응답 DTO가
 * {@link com.pairing.global.infrastructure.s3.CdnMappable} 을 구현해 직렬화 시점에 처리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile {

    private Long id;
    private Long ownerAccountId;
    private FilePurpose purpose;
    private String objectKey;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private LocalDateTime createdAt;

    private UploadedFile(Long id, Long ownerAccountId, FilePurpose purpose, String objectKey, String originalName,
                         String mimeType, Long sizeBytes, LocalDateTime createdAt) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.purpose = purpose;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
    }

    public static UploadedFile create(Long ownerAccountId, FilePurpose purpose, String objectKey,
                                      String originalName, String mimeType, Long sizeBytes) {
        return new UploadedFile(null, ownerAccountId, purpose, objectKey, originalName, mimeType, sizeBytes,
                LocalDateTime.now());
    }

    public static UploadedFile reconstitute(Long id, Long ownerAccountId, FilePurpose purpose, String objectKey,
                                            String originalName, String mimeType, Long sizeBytes,
                                            LocalDateTime createdAt) {
        return new UploadedFile(id, ownerAccountId, purpose, objectKey, originalName, mimeType, sizeBytes,
                createdAt);
    }
}
