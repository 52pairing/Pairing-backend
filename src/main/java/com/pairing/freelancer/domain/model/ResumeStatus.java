package com.pairing.freelancer.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 이력서 상태. 필수 항목을 다 채워야 완료되고, 완료된 이력서만 매칭에 쓰인다. */
@Getter
@RequiredArgsConstructor
public enum ResumeStatus {

    DRAFT("작성중"),
    COMPLETED("작성 완료");

    private final String label;
}
