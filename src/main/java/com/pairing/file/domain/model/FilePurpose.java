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
    INQUIRY_ATTACHMENT("1:1 문의 첨부파일", 10, "pdf, jpg, jpeg, png"),

    /**
     * 체결된 계약서. 사용자가 올리는 게 아니라 <b>서버가 만들어 굳혀 두는</b> 유일한 용도다.
     *
     * <p>계약은 5년 보관 대상이라 체결 시점 문서가 그대로 남아야 한다. 매번 다시 그리면 조항 문구나
     * 표기 규칙을 고쳤을 때 이미 체결된 계약서까지 바뀐다.
     */
    CONTRACT("계약서", 20, "pdf");

    private final String label;
    private final int maxSizeMb;
    private final String allowedExtensions;
}
