package com.pairing.freelancer.domain.model;

import com.pairing.freelancer.exception.FreelancerErrorCode;
import com.pairing.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이력서 작성 중 임시 저장(초안). 계정당 1건이고 저장할 때마다 덮어쓴다.
 *
 * <p>내용을 검증하지 않는다. 절반만 채운 상태로도 저장돼야 하는 게 이 기능의 목적이라,
 * 필수값 검사는 최종 등록({@link Resume})에서만 한다. 여기서 보는 건 크기뿐이다.
 *
 * <p>화면 입력값을 JSON 문자열 한 덩어리로 들고 있다. 초안은 조회·집계 대상이 아니라
 * 화면 복원용이어서 관계형으로 쪼갤 이유가 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeDraft {

    /** 이력서 한 벌이 다 들어가도 남는 크기. 무한정 받아 두면 저장소가 요청 하나로 망가진다. */
    public static final int MAX_PAYLOAD_LENGTH = 100_000;

    private Long id;
    private Long accountId;
    private String payload;
    private LocalDateTime updatedAt;

    private ResumeDraft(Long id, Long accountId, String payload, LocalDateTime updatedAt) {
        validate(accountId, payload);
        this.id = id;
        this.accountId = accountId;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }

    public static ResumeDraft create(Long accountId, String payload) {
        return new ResumeDraft(null, accountId, payload, LocalDateTime.now());
    }

    public static ResumeDraft reconstitute(Long id, Long accountId, String payload, LocalDateTime updatedAt) {
        return new ResumeDraft(id, accountId, payload, updatedAt);
    }

    /** 임시 저장은 덮어쓰기다. 초안을 여러 벌 쌓아 두면 어느 게 최신인지 화면이 알 수 없다. */
    public void replaceWith(String payload) {
        validate(this.accountId, payload);
        this.payload = payload;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validate(Long accountId, String payload) {
        if (accountId == null || payload == null || payload.isBlank()) {
            throw new BusinessException(FreelancerErrorCode.INVALID_RESUME_FIELD);
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new BusinessException(FreelancerErrorCode.DRAFT_TOO_LARGE);
        }
    }
}
