package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.PaymentMethodType;
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

/** payment_method 테이블 매핑. 카드번호/계좌번호는 암호문(BYTEA)만 저장한다. */
@Entity
@Table(name = "payment_method")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethodJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodType methodType;

    @Column(name = "card_number_enc")
    private byte[] cardNumberEnc;

    @Column(name = "card_brand", length = 30)
    private String cardBrand;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "card_holder", length = 50)
    private String cardHolder;

    @Column(name = "bank_code", length = 10)
    private String bankCode;

    @Column(name = "account_no_enc")
    private byte[] accountNoEnc;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_last4", length = 4)
    private String accountLast4;

    @Column(name = "account_holder", length = 50)
    private String accountHolder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public PaymentMethodJpaEntity(Long id, Long accountId, PaymentMethodType methodType, byte[] cardNumberEnc,
                                  String cardBrand, String cardLast4, String cardHolder, String bankCode,
                                  byte[] accountNoEnc, String accountLast4, String accountHolder,
                                  LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.methodType = methodType;
        this.cardNumberEnc = cardNumberEnc;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.cardHolder = cardHolder;
        this.bankCode = bankCode;
        this.accountNoEnc = accountNoEnc;
        this.accountLast4 = accountLast4;
        this.accountHolder = accountHolder;
        this.deletedAt = deletedAt;
    }
}
