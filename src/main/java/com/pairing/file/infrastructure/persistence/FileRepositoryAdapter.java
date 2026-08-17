package com.pairing.file.infrastructure.persistence;

import com.pairing.file.domain.model.UploadedFile;
import com.pairing.file.domain.repository.FileRepository;
import com.pairing.file.infrastructure.mapper.FileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FileRepositoryAdapter implements FileRepository {

    private final SpringDataFileRepository springDataRepository;
    private final FileMapper fileMapper;

    @Override
    public UploadedFile save(UploadedFile file) {
        FileJpaEntity saved = springDataRepository.save(fileMapper.toJpaEntity(file));
        return fileMapper.toDomain(saved);
    }

    @Override
    public Optional<UploadedFile> findById(Long id) {
        return springDataRepository.findById(id).map(fileMapper::toDomain);
    }

    @Override
    public List<UploadedFile> findByIdIn(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // JpaRepository 가 이미 제공하는 findAllById 를 쓴다. SpringDataFileRepository 에
        // 새 메서드를 추가할 필요가 없다.
        return springDataRepository.findAllById(ids).stream()
                .map(fileMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        springDataRepository.deleteById(id);
    }
}
