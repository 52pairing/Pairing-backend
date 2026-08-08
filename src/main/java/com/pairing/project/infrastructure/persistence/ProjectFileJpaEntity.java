package com.pairing.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * project_file 테이블 매핑. 프로젝트 첨부 자료 1건이다.
 *
 * <p>file_id 는 file 테이블의 FK 다. 파일 메타(원본명·크기·URL)는 file 도메인이 소유한다.
 * sort_order 로 화면 노출 순서를 유지한다.
 */
@Entity
@Table(name = "project_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectFileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectJpaEntity project;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public ProjectFileJpaEntity(Long id, Long fileId, int sortOrder) {
        this.id = id;
        this.fileId = fileId;
        this.sortOrder = sortOrder;
    }

    void assignProject(ProjectJpaEntity project) {
        this.project = project;
    }
}