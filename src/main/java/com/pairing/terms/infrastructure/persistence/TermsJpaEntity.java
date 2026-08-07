package com.pairing.terms.infrastructure.persistence;

import com.pairing.terms.domain.model.TermsCode;
import com.pairing.terms.domain.model.TermsType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** terms 테이블 매핑. (code, version) 유니크. */
@Entity
@Table(name = "terms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 50)
    private TermsCode code;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType type;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "target_role", length = 20)
    private String targetRole;

    @Column(name = "effective_at", nullable = false)
    private LocalDateTime effectiveAt;

    public TermsJpaEntity(Long id, TermsCode code, TermsType type, String version, String title, String content,
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
}
