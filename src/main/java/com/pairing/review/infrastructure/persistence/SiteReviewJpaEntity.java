package com.pairing.review.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사이트 후기. 계약 1건에 작성자당 한 번만 쓸 수 있다.
 *
 * <p>중복은 {@code ReviewService} 가 상호 평가 중복 검사에서 먼저 막지만, DB 제약을 함께 둔다.
 * 같은 요청이 동시에 두 번 들어오면 두 스레드 모두 "아직 없다"를 보고 통과할 수 있다.
 */
@Entity
@Table(name = "site_review", uniqueConstraints = {
        @UniqueConstraint(name = "uk_site_review_contract_writer",
                columnNames = {"contract_id", "writer_account_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteReviewJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "writer_account_id", nullable = false)
    private Long writerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_role", nullable = false, length = 10)
    private PartyRole writerRole;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "content", length = 500)
    private String content;

    /**
     * 홍보 활용 여부. 사용자에게 후기가 보이느냐를 결정하는 <b>유일한</b> 스위치다.
     *
     * <p>테이블의 {@code visibility} 컬럼은 매핑하지 않는다. 홍보를 끄면 이미 안 보이는데
     * 그 위에 공개 여부를 또 두는 것은 아무것도 바꾸지 않는 스위치였다. 컬럼은 기본값이
     * 있어 INSERT 에 지장이 없고, 지우는 것은 운영 배포 때 따로 정리한다.
     */
    @Column(name = "promoted", nullable = false)
    private boolean promoted;

    /**
     * 작성 시점의 프로젝트명·작성자명 사본.
     *
     * <p>둘 다 {@code nullable} 이다. 작성 시점에 못 찾을 수 있고(계정 삭제 등), 컬럼을 추가하기 전에
     * 쌓인 후기는 백필 전까지 비어 있다. 화면은 이미 값이 없는 경우를 처리하고 있다.
     */
    @Column(name = "project_title", length = 200)
    private String projectTitle;

    @Column(name = "writer_name", length = 100)
    private String writerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SiteReviewJpaEntity(Long id, Long contractId, Long projectId, Long writerAccountId,
                               PartyRole writerRole, int score, String content,
                               boolean promoted, String projectTitle, String writerName,
                               LocalDateTime createdAt) {
        this.id = id;
        this.contractId = contractId;
        this.projectId = projectId;
        this.writerAccountId = writerAccountId;
        this.writerRole = writerRole;
        this.score = score;
        this.content = content;
        this.promoted = promoted;
        this.projectTitle = projectTitle;
        this.writerName = writerName;
        this.createdAt = createdAt;
    }
}
