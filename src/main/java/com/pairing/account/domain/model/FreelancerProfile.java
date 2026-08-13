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
    private boolean matchingPaused;
    private String grade;
    private LocalDateTime gradeCheckedAt;
    private LocalDateTime deletedAt;

    private FreelancerProfile(Long id, Long accountId, LocalDate birthDate, String address, Long profileFileId,
                              boolean aiMatchingAgreed, boolean matchingPaused, String grade,
                              LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.birthDate = birthDate;
        this.address = address;
        this.profileFileId = profileFileId;
        this.aiMatchingAgreed = aiMatchingAgreed;
        this.matchingPaused = matchingPaused;
        this.grade = grade;
        this.gradeCheckedAt = gradeCheckedAt;
        this.deletedAt = deletedAt;
    }

    public static FreelancerProfile create(Long accountId, LocalDate birthDate) {
        if (accountId == null || birthDate == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new FreelancerProfile(null, accountId, birthDate, null, null, true, false, INITIAL_GRADE, null, null);
    }

    public static FreelancerProfile reconstitute(Long id, Long accountId, LocalDate birthDate, String address,
                                                 Long profileFileId, boolean aiMatchingAgreed, boolean matchingPaused,
                                                 String grade, LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        return new FreelancerProfile(id, accountId, birthDate, address, profileFileId, aiMatchingAgreed,
                matchingPaused, grade, gradeCheckedAt, deletedAt);
    }

    /**
     * 등급 산정 결과 반영. (정책 P01)
     *
     * <p>사용자가 바꿀 수 없다. 등급 도메인의 월간 산정만 이 메서드를 부른다.
     * {@code gradeCheckedAt} 은 <b>등급이 그대로여도 갱신한다</b> — "언제 확인했는가"가
     * "언제 바뀌었는가"보다 중요하다. 확인 시각이 안 움직이면 배치가 돌았는지 알 수 없다.
     */
    public void applyGrade(String grade, LocalDateTime checkedAt) {
        if (grade == null || grade.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.grade = grade;
        this.gradeCheckedAt = checkedAt;
    }

    /** 마이페이지 > 매칭 설정. {@code PUT /me/matching-settings}. */
    public void updateMatchingSettings(boolean aiMatchingAgreed, boolean matchingPaused) {
        this.aiMatchingAgreed = aiMatchingAgreed;
        this.matchingPaused = matchingPaused;
    }

    /** 마이페이지 기본 정보 수정. {@code matchingPaused} 는 별도 화면(매칭 설정) 책임이라 건드리지 않는다. */
    public void updateProfile(String address, Long profileFileId, boolean aiMatchingAgreed) {
        this.address = address;
        this.profileFileId = profileFileId;
        this.aiMatchingAgreed = aiMatchingAgreed;
    }
}
