package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 결제/정산 수단.
 *
 * <p>카드번호와 계좌번호는 암호문(byte[])만 들고 있다. 평문은 도메인에 올라오지 않는다.
 * 암복호화는 {@code DataEncryptionPort} 구현체가 담당한다.
 *
 * <p>화면에 "**** 6789" 를 그리려면 끝 4자리가 필요한데 암호문에서는 꺼낼 수 없다. 그래서
 * {@code cardLast4}/{@code accountLast4} 를 저장 시점에 따로 남긴다.
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
    private String cardHolder;
    private String bankCode;
    private byte[] accountNoEnc;
    private String accountLast4;
    private String accountHolder;
    private LocalDateTime deletedAt;

    private PaymentMethod(Long id, Long accountId, PaymentMethodType methodType, byte[] cardNumberEnc,
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

    /** {@code cardHolder} 는 가입 요청에 없는 값이라 null 로 시작하고, 마이페이지 수정에서 채워진다. */
    public static PaymentMethod createCard(Long accountId, byte[] cardNumberEnc, String cardBrand,
                                           String cardLast4) {
        if (accountId == null || cardNumberEnc == null
                || cardLast4 == null || cardLast4.length() != CARD_LAST4_LENGTH) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new PaymentMethod(null, accountId, PaymentMethodType.CARD, cardNumberEnc, cardBrand, cardLast4,
                null, null, null, null, null, null);
    }

    public static PaymentMethod createBankAccount(Long accountId, String bankCode, byte[] accountNoEnc,
                                                  String accountLast4, String accountHolder) {
        if (accountId == null || bankCode == null || bankCode.isBlank() || accountNoEnc == null
                || accountLast4 == null || accountLast4.length() != CARD_LAST4_LENGTH
                || accountHolder == null || accountHolder.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new PaymentMethod(null, accountId, PaymentMethodType.BANK_ACCOUNT, null, null, null, null,
                bankCode, accountNoEnc, accountLast4, accountHolder, null);
    }

    public static PaymentMethod reconstitute(Long id, Long accountId, PaymentMethodType methodType,
                                             byte[] cardNumberEnc, String cardBrand, String cardLast4,
                                             String cardHolder, String bankCode, byte[] accountNoEnc,
                                             String accountLast4, String accountHolder,
                                             LocalDateTime deletedAt) {
        return new PaymentMethod(id, accountId, methodType, cardNumberEnc, cardBrand, cardLast4, cardHolder,
                bankCode, accountNoEnc, accountLast4, accountHolder, deletedAt);
    }

    /**
     * 마이페이지 &gt; 결제수단에서 카드를 교체한다. 신규 등록·삭제는 없고 가입 시 만들어진 건을 수정만 한다.
     *
     * <p>카드 행에 계좌 값을 덮어쓰면 한 행이 두 수단을 겸하게 되므로 종류가 다르면 막는다.
     */
    public void updateCard(byte[] cardNumberEnc, String cardBrand, String cardLast4, String cardHolder) {
        if (this.methodType != PaymentMethodType.CARD) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_STATE);
        }
        if (cardNumberEnc == null || cardLast4 == null || cardLast4.length() != CARD_LAST4_LENGTH) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.cardNumberEnc = cardNumberEnc;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.cardHolder = cardHolder;
    }

    /** 마이페이지 &gt; 결제수단에서 정산 계좌를 교체한다. */
    public void updateBankAccount(String bankCode, byte[] accountNoEnc, String accountLast4,
                                  String accountHolder) {
        if (this.methodType != PaymentMethodType.BANK_ACCOUNT) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_STATE);
        }
        if (bankCode == null || bankCode.isBlank() || accountNoEnc == null
                || accountLast4 == null || accountLast4.length() != CARD_LAST4_LENGTH
                || accountHolder == null || accountHolder.isBlank()) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        this.bankCode = bankCode;
        this.accountNoEnc = accountNoEnc;
        this.accountLast4 = accountLast4;
        this.accountHolder = accountHolder;
    }

    public boolean isCard() {
        return this.methodType == PaymentMethodType.CARD;
    }

    /**
     * 은행 이름. 저장하는 값은 기관코드뿐이라 조회 시점에 코드로 찾는다.
     *
     * <p>이름을 따로 저장하면 은행명이 바뀔 때 기존 행이 옛 이름으로 남는다.
     */
    public String getBankName() {
        return BankCode.find(this.bankCode)
                .map(BankCode::getLabel)
                .orElse(null);
    }

    /**
     * 카드사 이름. 저장하는 값은 {@link CardCompany} 의 enum 이름이라 조회 시점에 한글명으로 푼다.
     *
     * <p>이 기능이 들어오기 전에 저장된 행에는 한글 카드사명이 그대로 들어 있다. 그런 값은 못 찾으므로
     * <b>저장된 문자열을 그대로 돌려준다</b> — 옛 데이터 때문에 마이페이지가 빈칸이 되면 안 된다.
     */
    public String getCardBrandName() {
        return CardCompany.find(this.cardBrand)
                .map(CardCompany::getLabel)
                .orElse(this.cardBrand);
    }

    /** 화면 표시용 이름. 카드는 "신한카드 **** 5678", 계좌는 "신한은행 **** 6789". */
    public String getDisplayName() {
        if (isCard()) {
            return joinMasked(getCardBrandName(), this.cardLast4);
        }
        return joinMasked(getBankName(), this.accountLast4);
    }

    private String joinMasked(String label, String last4) {
        return Optional.ofNullable(label).orElse("") + " **** " + Optional.ofNullable(last4).orElse("");
    }
}
