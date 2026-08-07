package com.pairing.file.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataFileRepository extends JpaRepository<FileJpaEntity, Long> {
}
