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

    public static ClientProfile create(Long accountId, String companyName, String businessNo,
                                       BusinessField businessField, EmployeeCount employeeCount) {
        if (accountId == null || companyName == null || companyName.isBlank()
                || businessNo == null || businessNo.length() != BUSINESS_NO_LENGTH
                || businessField == null || employeeCount == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new ClientProfile(null, accountId, companyName, businessNo, businessField, employeeCount,
                null, null, INITIAL_GRADE, null, null);
    }

    public static ClientProfile reconstitute(Long id, Long accountId, String companyName, String businessNo,
                                             BusinessField businessField, EmployeeCount employeeCount,
                                             String address, Long logoFileId, String grade,
                                             LocalDateTime gradeCheckedAt, LocalDateTime deletedAt) {
        return new ClientProfile(id, accountId, companyName, businessNo, businessField, employeeCount,
                address, logoFileId, grade, gradeCheckedAt, deletedAt);
    }
}
