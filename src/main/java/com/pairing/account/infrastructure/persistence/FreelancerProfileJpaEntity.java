package com.pairing.account.infrastructure.persistence;

import jakarta.persistence.Column;
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

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "profile_file_id")
    private Long profileFileId;

    @Column(name = "ai_matching_agreed", nullable = false)
    private boolean aiMatchingAgreed;

    @Column(name = "grade", nullable = false, length = 20)
    private String grade;

    @Column(name = "grade_checked_at")
    private LocalDateTime gradeCheckedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public FreelancerProfileJpaEntity(Long id, Long accountId, LocalDate birthDate, String address,
                                      Long profileFileId, boolean aiMatchingAgreed, String grade,
                                      LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.birthDate = birthDate;
        this.address = address;
        this.profileFileId = profileFileId;
        this.aiMatchingAgreed = aiMatchingAgreed;
        this.grade = grade;
        this.gradeCheckedAt = gradeCheckedAt;
        this.deletedAt = deletedAt;
    }
}
