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

    @Column(name = "issuer_score", length = 100)
    private String issuerScore;

    @Column(name = "note", length = 255)
    private String note;

    public ResumeCertificateEmbeddable(LocalDate acquiredDate, String name, String issuerScore, String note) {
        this.acquiredDate = acquiredDate;
        this.name = name;
        this.issuerScore = issuerScore;
        this.note = note;
    }
}
