package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 프리랜서 프로필. 계정과 1:1이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FreelancerProfile {

    /** 가입 시 부여되는 초기 등급. */
    private static final String INITIAL_GRADE = "JUNIOR";

    private Long id;
    private Long accountId;
    private LocalDate birthDate;
    private String address;
    private Long profileFileId;
    private boolean aiMatchingAgreed;
    private String grade;
    private LocalDateTime gradeCheckedAt;
    private LocalDateTime deletedAt;

    private FreelancerProfile(Long id, Long accountId, LocalDate birthDate, String address, Long profileFileId,
                              boolean aiMatchingAgreed, String grade, LocalDateTime gradeCheckedAt,
                              LocalDateTime deletedAt) {
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

    public static FreelancerProfile create(Long accountId, LocalDate birthDate) {
        if (accountId == null || birthDate == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new FreelancerProfile(null, accountId, birthDate, null, null, true, INITIAL_GRADE, null, null);
    }

    public static FreelancerProfile reconstitute(Long id, Long accountId, LocalDate birthDate, String address,
                                                 Long profileFileId, boolean aiMatchingAgreed, String grade,
                                                 LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        return new FreelancerProfile(id, accountId, birthDate, address, profileFileId, aiMatchingAgreed,
                grade, gradeCheckedAt, deletedAt);
    }
}
