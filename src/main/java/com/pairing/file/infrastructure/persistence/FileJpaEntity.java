package com.pairing.file.infrastructure.persistence;

import com.pairing.file.domain.model.FilePurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_account_id", nullable = false)
    private Long ownerAccountId;

    // FilePurpose. 컬럼명은 file_group (다른 도메인 문서에도 이 이름으로 언급됨)
    @Enumerated(EnumType.STRING)
    @Column(name = "file_group", nullable = false, length = 20)
    private FilePurpose purpose;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public FileJpaEntity(Long id, Long ownerAccountId, FilePurpose purpose, String objectKey, String originalName,
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
}
