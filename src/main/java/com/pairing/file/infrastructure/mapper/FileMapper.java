package com.pairing.file.infrastructure.mapper;

import com.pairing.file.domain.model.UploadedFile;
import com.pairing.file.infrastructure.persistence.FileJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface FileMapper {

    FileJpaEntity toJpaEntity(UploadedFile file);

    default UploadedFile toDomain(FileJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return UploadedFile.reconstitute(entity.getId(), entity.getOwnerAccountId(), entity.getPurpose(),
                entity.getObjectKey(), entity.getOriginalName(), entity.getMimeType(), entity.getSizeBytes(),
                entity.getCreatedAt());
    }
}
