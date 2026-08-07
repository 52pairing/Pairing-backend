package com.pairing.terms.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 약관 문서의 성격.
 *
 * <p>둘을 한 테이블에 두되 노출 위치를 가른다. 가입 화면은 {@code AGREEMENT} 만 보여주고,
 * 푸터의 문서 링크는 둘 다 보여준다.
 *
 * <p>개인정보 처리방침을 동의 항목으로 만들면 안 된다. 보호법 제30조는 '수립·공개' 의무이고,
 * 동의를 받아야 하는 문서가 아니다. (개인정보 처리방침 작성지침 2026.4)
 */
@Getter
@RequiredArgsConstructor
public enum TermsType {

    AGREEMENT("동의 항목"),
    POLICY("게시 문서");

    private final String label;
}
