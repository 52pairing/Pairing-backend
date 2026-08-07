package com.pairing.project.application.usecase;

import com.pairing.project.application.result.ProjectPositionSummary;

import java.util.List;

/**
 * 프로젝트 조회 인바운드 포트. 매칭·협상·계약 도메인이 이 인터페이스만 호출한다.
 *
 * <p><b>accountId 와 clientProfileId 는 다른 값이다.</b> {@code project.client_id} 는
 * {@code client_profile.id} 를 가리킨다. 변환은 이 포트 안에서 처리하므로
 * 호출부는 {@code @CurrentAccountId} 값을 그대로 넘기면 된다.
 *
 * <p>대상이 없으면 {@code BusinessException} 을 던진다. null 을 반환하지 않는다.
 */
public interface ProjectQueryUseCase {

    /** 접근 제어용. 프로젝트가 없으면 false. */
    boolean isOwnedBy(Long projectId, Long accountId);

    /** 상태와 무관하게 전부 내려간다(취소·종료 포함). 없으면 빈 리스트. */
    List<Long> findProjectIdsByAccountId(Long accountId);

    /** 등급·회사명 조회 키. account 도메인의 findClientProfileById 에 그대로 넣는다. */
    Long findClientProfileId(Long projectId);

    int findHeadcount(Long positionId);

    ProjectPositionSummary findProjectPositionSummary(Long projectId, Long positionId);
}