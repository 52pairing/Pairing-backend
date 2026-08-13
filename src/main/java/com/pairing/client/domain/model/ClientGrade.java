package com.pairing.client.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 클라이언트 등급. 후보 노출 수와 혜택이 달라진다. (골드 3.0·10건 / 다이아 4.0·20건) */
@Getter
@RequiredArgsConstructor
public enum ClientGrade {

    SILVER("실버"),
    GOLD("골드"),
    DIAMOND("다이아");

    private final String label;

    /**
     * 저장된 등급 코드를 등급으로 바꾼다. 비어 있거나 목록에 없는 코드(옛 코드 등)면 기본 등급이다.
     *
     * <p>DB 문자열을 {@code valueOf} 로 바로 파싱하면 값이 하나 어긋난 계정에서 조회 API 가
     * 통째로 500 이 된다. 등급은 화면의 한 줄이라 마이페이지를 못 열게 만들 이유가 없다.
     */
    public static ClientGrade of(String code) {
        for (ClientGrade grade : values()) {
            if (grade.name().equals(code)) {
                return grade;
            }
        }
        return SILVER;
    }
}
