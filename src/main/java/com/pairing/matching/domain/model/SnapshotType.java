package com.pairing.matching.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 매칭 시점 정보 동결(스냅샷) 대상.
 *
 * <p>매칭 진행 중에 프로필/프로젝트 정보를 수정해도, 이미 진행 중인 매칭·협상은
 * 이 스냅샷을 기준으로 삼는다(R17/R21/R30). PROJECT/POSITION은 등록 후 수정이 불가능해
 * 실질적으로는 검수 통과 시점 값과 항상 같지만, 협상 쪽에서 매칭 도메인 테이블만 보고도
 * 조회할 수 있도록 동일하게 얼려둔다.
 */
@Getter
@RequiredArgsConstructor
public enum SnapshotType {

    PROJECT("프로젝트"),
    POSITION("포지션"),
    FREELANCER("프리랜서");

    private final String label;
}
