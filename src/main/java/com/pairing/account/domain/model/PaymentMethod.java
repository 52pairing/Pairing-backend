package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제/정산 수단.
 *
 * <p>카드번호와 계좌번호는 암호문(byte[])만 들고 있다. 평문은 도메인에 올라오지 않는다.
 * 암복호화는 {@code DataEncryptionPort} 구현체가 담당한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod {

    private static final int CARD_LAST4_LENGTH = 4;

    private Long id;
    private Long accountId;
    private PaymentMethodType methodType;
    private byte[] cardNumberEnc;
    private String cardBrand;
    private String cardLast4;
    private String bankCode;
    private byte[] accountNoEnc;
    private String accountHolder;
    private LocalDateTime deletedAt;

    private PaymentMethod(Long id, Long accountId, PaymentMethodType methodType, byte[] cardNumberEnc,
                          String cardBrand, String cardLast4, String bankCode, byte[] accountNoEnc,
                          String accountHolder, LocalDateTime deletedAt) {
        this.id = id;
        this.accountId = accountId;
        this.methodType = methodType;
        this.cardNumberEnc = cardNumberEnc;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.bankCode = bankCode;
        this.accountNoEnc = accountNoEnc;
        this.accountHolder = accountHolder;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethod createCard(Long accountId, byte[] cardNumberEnc, String cardBrand,
                                           String cardLast4) {
        if (accountId == null || cardNumberEnc == null
                || cardLast4 == null || cardLast4.length() != CARD_LAST4_LENGTH) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new PaymentMethod(null, accountId, PaymentMethodType.CARD, cardNumberEnc, cardBrand, cardLast4,
                null, null, null, null);
    }

    public static PaymentMethod createBankAccount(Long accountId, String bankCode, byte[] accountNoEnc,
                                                  String accountHolder) {
        if (accountId == null || bankCode == null || bankCode.isBlank() || accountNoEnc == null
                || accountHolder == null || accountHolder.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new PaymentMethod(null, accountId, PaymentMethodType.BANK_ACCOUNT, null, null, null,
                bankCode, accountNoEnc, accountHolder, null);
    }

    public static PaymentMethod reconstitute(Long id, Long accountId, PaymentMethodType methodType,
                                             byte[] cardNumberEnc, String cardBrand, String cardLast4,
                                             String bankCode, byte[] accountNoEnc, String accountHolder,
                                             LocalDateTime deletedAt) {
        return new PaymentMethod(id, accountId, methodType, cardNumberEnc, cardBrand, cardLast4, bankCode,
                accountNoEnc, accountHolder, deletedAt);
    }
}
