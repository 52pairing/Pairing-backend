package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** client_profile 테이블 매핑. account 와 1:1. */
@Entity
@Table(name = "client_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    // CHAR(10) 컬럼이다. JdbcTypeCode 를 지정하지 않으면 Hibernate 가 varchar 를 기대해
    // ddl-auto=validate 단계에서 타입 불일치로 기동이 실패한다.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "business_no", nullable = false, length = 10)
    private String businessNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_field", nullable = false, length = 40)
    private BusinessField businessField;

    @Enumerated(EnumType.STRING)
    @Column(name = "employee_count", nullable = false, length = 30)
    private EmployeeCount employeeCount;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "logo_file_id")
    private Long logoFileId;

    @Column(name = "grade", nullable = false, length = 20)
    private String grade;

    @Column(name = "grade_checked_at")
    private LocalDateTime gradeCheckedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public ClientProfileJpaEntity(Long id, Long accountId, String companyName, String businessNo,
                                  BusinessField businessField, EmployeeCount employeeCount, String address,
                                  Long logoFileId, String grade, LocalDateTime gradeCheckedAt,
                                  LocalDateTime deletedAt) {
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
}
