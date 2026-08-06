package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 은행 코드(금융결제원 기관코드).
 *
 * <p>DB에는 enum 이름이 아니라 숫자 코드("088")를 저장한다. 계좌 이체·정산 연동에서 쓰는 값이
 * 이 코드라서, 우리 이름을 저장하면 연동할 때마다 변환표가 필요해진다.
 *
 * <p>목록이 부족하면 여기에 추가한다. 화면 목록은 {@code GET /api/v1/meta/banks} 가 내려준다.
 */
@Getter
@RequiredArgsConstructor
public enum BankCode {

    KDB("002", "KDB산업은행"),
    IBK("003", "IBK기업은행"),
    KOOKMIN("004", "KB국민은행"),
    SUHYUP("007", "수협은행"),
    NONGHYUP("011", "NH농협은행"),
    LOCAL_NONGHYUP("012", "지역농축협"),
    WOORI("020", "우리은행"),
    SC("023", "SC제일은행"),
    CITI("027", "한국씨티은행"),
    DAEGU("031", "iM뱅크(대구)"),
    BUSAN("032", "부산은행"),
    GWANGJU("034", "광주은행"),
    JEJU("035", "제주은행"),
    JEONBUK("037", "전북은행"),
    GYEONGNAM("039", "경남은행"),
    SAEMAUL("045", "새마을금고"),
    SHINHYUP("048", "신협"),
    SAVINGS("050", "저축은행"),
    FOREST("064", "산림조합"),
    POST("071", "우체국"),
    HANA("081", "하나은행"),
    SHINHAN("088", "신한은행"),
    KBANK("089", "케이뱅크"),
    KAKAOBANK("090", "카카오뱅크"),
    TOSSBANK("092", "토스뱅크");

    private final String code;
    private final String label;

    public static Optional<BankCode> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(bank -> bank.code.equals(code.trim()))
                .findFirst();
    }
}
