package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 클라이언트 사업 분야. 요구사항 R13의 20개 항목과 1:1로 대응한다. */
@Getter
@RequiredArgsConstructor
public enum BusinessField {

    IT_CONTENTS_AI("IT/컨텐츠/AI"),
    GAME("게임"),
    SALES_DISTRIBUTION_LOGISTICS("판매/유통/물류"),
    MANUFACTURING("제조"),
    ADVANCED_SCIENCE("첨단/과학기술"),
    OTHER_SERVICE("기타 서비스업"),
    FINANCE("금융"),
    EDUCATION("교육"),
    REAL_ESTATE("부동산"),
    ARTS_SPORTS_LEISURE("예술/스포츠/여가"),
    HEALTH_WELFARE("보건/복지"),
    CONSTRUCTION("건설"),
    LODGING_FOOD("숙박/요식"),
    AGRICULTURE_FISHERY("농림어업"),
    MARKETING("마케팅"),
    WATER_ENVIRONMENT("상수도/환경"),
    ELECTRICITY_GAS("전기/가스"),
    PUBLIC_ADMIN_DEFENSE("공공행정/국방"),
    MINING("광산업"),
    MEDICAL_HEALTHCARE("의료/헬스케어");

    private final String label;
}
