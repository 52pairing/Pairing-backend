package com.pairing.file.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파일 용도. 용도마다 허용 확장자와 크기 상한이 다르다.
 *
 * <p>file 테이블의 file_group 컬럼에 그대로 저장한다.
 */
@Getter
@RequiredArgsConstructor
public enum FilePurpose {

    PROFILE_IMAGE("프로필 사진", 5, "jpg, jpeg, png"),
    COMPANY_LOGO("기업 로고", 5, "jpg, jpeg, png"),
    PORTFOLIO("포트폴리오", 100, "pdf"),
    PROJECT_FILE("프로젝트 자료", 100, "pdf, jpg, jpeg, png"),
    SIGNATURE("서명 이미지", 5, "jpg, jpeg, png"),
    INQUIRY_ATTACHMENT("1:1 문의 첨부파일", 10, "pdf, jpg, jpeg, png");

    private final String label;
    private final int maxSizeMb;
    private final String allowedExtensions;
}
