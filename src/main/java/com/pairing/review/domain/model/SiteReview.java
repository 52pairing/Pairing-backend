package com.pairing.review.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.review.exception.ReviewErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사이트(플랫폼) 이용 후기. (요구사항 R22, R40)
 *
 * <p>후기 원문은 <b>관리자 화면 밖으로 나가지 않는다.</b> 사용자가 보는 것은 관리자가 홍보로 고른
 * 후기와 평균 별점뿐이다. 그래서 노출 여부를 정하는 스위치는 {@code promoted} 하나다.
 *
 * <p>예전에는 공개/비공개를 따로 뒀는데, 홍보를 끄면 이미 안 보이는 상태라 아무것도 바꾸지 않는
 * 스위치였다. 관리자가 두 번 눌러야 했고 어긋난 조합까지 관리해야 해서 없앴다.
 *
 * <p>작성 후 내용과 별점은 바뀌지 않는다.
 *
 * <h2>프로젝트명·작성자명을 복사해 둔다</h2>
 *
 * <p>화면에는 이름이 나가야 하는데 이 후기가 가진 건 {@code projectId}·{@code writerAccountId} 뿐이라,
 * 예전에는 후기마다 프로젝트와 계정을 다시 읽었다. 메인 화면이 6건을 보여주면 그 조회가 6번씩 늘었다.
 *
 * <p><b>후기는 한 번 쓰면 고칠 수 없다.</b> 그래서 작성 시점의 이름을 그대로 복사해 두는 편이
 * 의미상으로도 맞다 — 이 후기는 "그때 그 프로젝트"에 대한 기록이지 지금 이름이 무엇인지는 상관이 없다.
 * 프로젝트가 지워져도 후기는 남아야 한다는 기존 동작과도 같은 방향이다.
 *
 * <p>이름은 <b>가리지 않은 원본</b>을 담는다. 마스킹은 보는 화면마다 규칙이 다를 수 있고
 * 조회를 더 하지도 않으므로, 읽는 쪽에서 처리한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SiteReview {

    private Long id;
    private Long contractId;
    private Long projectId;
    private Long writerAccountId;
    private PartyRole writerRole;
    private int score;
    private String content;
    private boolean promoted;

    /** 작성 시점의 프로젝트명. 못 찾았으면 null 이고, 화면은 프로젝트명만 비운다. */
    private String projectTitle;

    /** 작성 시점의 작성자명(클라이언트는 기업명). 못 찾았으면 null 이고, 화면은 역할 이름으로 대체한다. */
    private String writerName;

    private LocalDateTime createdAt;

    private SiteReview(Long id, Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                       int score, String content, boolean promoted, String projectTitle, String writerName,
                       LocalDateTime createdAt) {
        if (contractId == null || projectId == null || writerAccountId == null || writerRole == null) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
        if (score < 1 || score > 5) {
            throw new BusinessException(ReviewErrorCode.INVALID_REVIEW_FIELD);
        }
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

    /**
     * 작성 시에는 홍보 대상이 아니다. 관리자가 골라서 켠다.
     *
     * <p>{@code projectTitle}·{@code writerName} 은 부르는 쪽이 찾아서 넘긴다. 여기서 조회하면
     * 도메인이 다른 도메인을 알게 되고, 무엇보다 <b>작성 시점의 값</b>이라는 뜻이 흐려진다.
     */
    public static SiteReview create(Long contractId, Long projectId, Long writerAccountId, PartyRole writerRole,
                                    int score, String content, String projectTitle, String writerName) {
        return new SiteReview(null, contractId, projectId, writerAccountId, writerRole, score, content,
                false, projectTitle, writerName, LocalDateTime.now());
    }

    public static SiteReview reconstitute(Long id, Long contractId, Long projectId, Long writerAccountId,
                                          PartyRole writerRole, int score, String content, boolean promoted,
                                          String projectTitle, String writerName, LocalDateTime createdAt) {
        return new SiteReview(id, contractId, projectId, writerAccountId, writerRole, score, content,
                promoted, projectTitle, writerName, createdAt);
    }
}
