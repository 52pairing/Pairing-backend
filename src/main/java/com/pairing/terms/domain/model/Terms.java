package com.pairing.terms.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관. 버전별로 행을 남기며 기존 행을 수정하지 않는다.
 *
 * <p>{@code targetRole}은 CLIENT / FREELANCER / null(공통)이다. account 도메인의 Role enum을
 * 끌어오면 두 도메인이 묶이므로 문자열로 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms {

    private Long id;
    private TermsCode code;
    private TermsType type;
    private String version;
    private String title;
    private String content;
    private boolean required;
    private String targetRole;
    private LocalDateTime effectiveAt;

    private Terms(Long id, TermsCode code, TermsType type, String version, String title, String content,
                  boolean required, String targetRole, LocalDateTime effectiveAt) {
        this.id = id;
        this.code = code;
        this.type = type;
        this.version = version;
        this.title = title;
        this.content = content;
        this.required = required;
        this.targetRole = targetRole;
        this.effectiveAt = effectiveAt;
    }

    public static Terms reconstitute(Long id, TermsCode code, TermsType type, String version, String title,
                                     String content, boolean required, String targetRole,
                                     LocalDateTime effectiveAt) {
        return new Terms(id, code, type, version, title, content, required, targetRole, effectiveAt);
    }

    /** 가입 화면에서 동의를 받아야 하는 항목인지. 게시용 문서는 false 다. */
    public boolean isAgreement() {
        return this.type == TermsType.AGREEMENT;
    }

    /** 해당 역할에게 보여야 하는 약관인지. targetRole이 없으면 공통 약관이다. */
    public boolean appliesTo(String role) {
        return this.targetRole == null || this.targetRole.equals(role);
    }
}
