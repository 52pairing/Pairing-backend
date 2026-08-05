package com.pairing.example.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "examples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExampleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active;

    // S3 object key(상대경로)만 저장한다. CDN 절대 URL을 저장하면 CDN 주소가 바뀔 때 전 데이터를 마이그레이션해야 한다.
    @Column(length = 512)
    private String imageUrl;

    // 도메인 엔티티의 데이터를 받아 JPA 엔티티를 생성하는 생성자
    public ExampleJpaEntity(Long id, String name, boolean active, String imageUrl) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.imageUrl = imageUrl;
    }
}