package com.pairing.account.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** freelancer_profile 테이블 매핑. account 와 1:1. */
@Entity
@Table(name = "freelancer_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreelancerProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    // 한 줄로 합친 주소. 나눠 담기 전 가입한 행에는 이 값만 있다.
    @Column(name = "address", length = 255)
    private String address;

    @Embedded
    private AddressEmbeddable addressParts;

    @Column(name = "profile_file_id")
    private Long profileFileId;

    @Column(name = "ai_matching_agreed", nullable = false)
    private boolean aiMatchingAgreed;

    // columnDefinition으로 기본값을 명시한다 — 실제 스키마(db/init/02-create-schema.sql)에도
    // DEFAULT FALSE라, 이 컬럼을 모르는 기존 raw SQL INSERT(다른 도메인 통합테스트의 시드 헬퍼)가
    // 깨지지 않는다.
    @Column(name = "matching_paused", nullable = false, columnDefinition = "boolean default false")
    private boolean matchingPaused;

    @Column(name = "grade", nullable = false, length = 20)
    private String grade;

    @Column(name = "grade_checked_at")
    private LocalDateTime gradeCheckedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public FreelancerProfileJpaEntity(Long id, Long accountId, LocalDate birthDate, String address,
                                      AddressEmbeddable addressParts, Long profileFileId,
                                      boolean aiMatchingAgreed, boolean matchingPaused, String grade,
                                      LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.birthDate = birthDate;
        this.address = address;
        this.addressParts = addressParts;
        this.profileFileId = profileFileId;
        this.aiMatchingAgreed = aiMatchingAgreed;
        this.matchingPaused = matchingPaused;
        this.grade = grade;
        this.gradeCheckedAt = gradeCheckedAt;
        this.deletedAt = deletedAt;
    }
}
