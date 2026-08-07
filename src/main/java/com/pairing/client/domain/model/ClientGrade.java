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
}
