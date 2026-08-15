package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 국내 카드사.
 *
 * <p>DB({@code payment_method.card_brand})에는 한글 카드사명이 아니라 <b>enum 이름</b>("SHINHAN")을 저장한다.
 * 카드사명은 바뀔 수 있는 값이라("국민카드" → "KB국민카드") 이름을 그대로 저장하면 기존 행이 옛 표기로 남는다.
 * 화면에 찍는 한글명은 조회 시점에 {@link #getLabel()} 로 붙인다.
 *
 * <p>{@link BankCode} 와 달리 코드값을 따로 두지 않는다. 은행은 금융결제원 기관코드("088")가 이체 연동에서
 * 쓰이는 외부 규격이라 그 값을 저장해야 하지만, 카드사는 그런 외부 코드가 없어 enum 이름이 곧 코드다.
 *
 * <p>목록이 부족하면 여기에 추가한다. 화면 목록은 {@code GET /api/v1/meta/card-companies} 가 내려준다.
 */
@Getter
@RequiredArgsConstructor
public enum CardCompany {

    // 전업 카드사
    BC("BC카드"),
    KB("KB국민카드"),
    HANA("하나카드"),
    SAMSUNG("삼성카드"),
    SHINHAN("신한카드"),
    HYUNDAI("현대카드"),
    LOTTE("롯데카드"),
    WOORI("우리카드"),

    // 은행·기타 겸영
    NH("NH농협카드"),
    IBK("IBK기업은행카드"),
    CITI("씨티카드"),
    SC("SC제일은행카드"),
    SUHYUP("수협카드"),
    DAEGU("iM카드(대구)"),
    BUSAN("부산카드"),
    GYEONGNAM("경남카드"),
    GWANGJU("광주카드"),
    JEONBUK("전북카드"),
    JEJU("제주카드"),
    SAEMAUL("새마을금고카드"),
    SHINHYUP("신협카드"),
    POST("우체국카드"),
    KBANK("케이뱅크카드"),
    KAKAOBANK("카카오뱅크카드"),
    TOSSBANK("토스뱅크카드");

    private final String label;

    /**
     * 저장된 값으로 카드사를 찾는다. 못 찾으면 empty.
     *
     * <p>이 기능이 들어오기 전에 저장된 행에는 한글 카드사명("신한카드")이 그대로 들어 있다.
     * 그 값은 여기서 empty 로 떨어지고, 조회하는 쪽이 저장된 문자열을 그대로 보여준다
     * ({@link PaymentMethod#getCardBrandName()}). 옛 데이터 때문에 마이페이지가 깨지면 안 된다.
     */
    public static Optional<CardCompany> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim();
        return Arrays.stream(values())
                .filter(company -> company.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
