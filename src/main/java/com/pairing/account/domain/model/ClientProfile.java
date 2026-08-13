package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 클라이언트(기업) 프로필. 계정과 1:1이다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientProfile {

    /** 가입 시 부여되는 초기 등급. 등급 산정은 별도 도메인이 갱신한다. */
    private static final String INITIAL_GRADE = "SILVER";

    private static final int BUSINESS_NO_LENGTH = 10;

    private Long id;
    private Long accountId;
    private String companyName;
    private String businessNo;
    private BusinessField businessField;
    private EmployeeCount employeeCount;
    private String address;
    private Long logoFileId;
    private String grade;
    private LocalDateTime gradeCheckedAt;
    private LocalDateTime deletedAt;

    private ClientProfile(Long id, Long accountId, String companyName, String businessNo,
                          BusinessField businessField, EmployeeCount employeeCount, String address,
                          Long logoFileId, String grade, LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.companyName = companyName;
        this.businessNo = businessNo;
        this.businessField = businessField;
        this.employeeCount = employeeCount;
        this.address = address;
        this.logoFileId = logoFileId;
        this.grade = grade;
        this.gradeCheckedAt = gradeCheckedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * 신규 가입용. address 는 계약서 갑 표시에 쓰이므로 가입 시점부터 필수다.
     * 기존 데이터에는 null 이 있을 수 있어 {@link #reconstitute} 는 검증하지 않는다.
     */
    public static ClientProfile create(Long accountId, String companyName, String businessNo,
                                       BusinessField businessField, EmployeeCount employeeCount,
                                       String address) {
        if (accountId == null || companyName == null || companyName.isBlank()
                || businessNo == null || businessNo.length() != BUSINESS_NO_LENGTH
                || businessField == null || employeeCount == null
                || address == null || address.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new ClientProfile(null, accountId, companyName, businessNo, businessField, employeeCount,
                address, null, INITIAL_GRADE, null, null);
    }

    public static ClientProfile reconstitute(Long id, Long accountId, String companyName, String businessNo,
                                             BusinessField businessField, EmployeeCount employeeCount,
                                             String address, Long logoFileId, String grade,
                                             LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        return new ClientProfile(id, accountId, companyName, businessNo, businessField, employeeCount,
                address, logoFileId, grade, gradeCheckedAt, deletedAt);
    }

    /** 마이페이지 기업정보 수정. 사업자등록번호·사업 분야는 여기서 바꿀 수 없다. */
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

    /**
     * 기업 정보 수정. 사업자등록번호·사업 분야는 바꾸지 않는다.
     *
     * <p>{@code logoFileId} 는 null 이면 기존 로고를 그대로 둔다. 수정 화면이 로고를 건드리지 않고
     * 저장하는 경우가 대부분이라, null 을 "지움"으로 보면 매번 로고가 날아간다.
     */
    public void updateCompanyInfo(String companyName, EmployeeCount employeeCount, String address,
                                  Long logoFileId) {
        if (companyName == null || companyName.isBlank() || employeeCount == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.companyName = companyName;
        this.employeeCount = employeeCount;
        this.address = address;
        if (logoFileId != null) {
            this.logoFileId = logoFileId;
        }
    }
}
