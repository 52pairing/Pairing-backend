package com.pairing.freelancer.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** resume_certificate 테이블의 한 행. resume 에 종속된다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeCertificateEmbeddable {

    @Column(name = "acquired_date", nullable = false)
    private LocalDate acquiredDate;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "issuer", length = 100)
    private String issuer;

    @Column(name = "score", length = 50)
    private String score;

    @Column(name = "note", length = 255)
    private String note;

    public ResumeCertificateEmbeddable(LocalDate acquiredDate, String name, String issuer, String score,
                                       String note) {
        this.acquiredDate = acquiredDate;
        this.name = name;
        this.issuer = issuer;
        this.score = score;
        this.note = note;
    }
}
