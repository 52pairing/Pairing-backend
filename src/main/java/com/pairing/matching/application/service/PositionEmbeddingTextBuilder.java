package com.pairing.matching.application.service;

import com.pairing.matching.application.result.ProjectPositionSummary;

import java.util.stream.Stream;

/**
 * 포지션 임베딩용 텍스트 조립. 최초 모집 시작(임베딩 최초 생성)과 모집 시작 후 프로젝트 수정
 * (임베딩 재생성) 양쪽에서 같은 규칙을 써야 해서 공유한다.
 *
 * <p><b>자유 서술만 넣는다(2026-08-11 재설계).</b> 직무·요구스킬·최소경력·근무조건·기간을 전부
 * 뺐다. 직무는 하드필터가 이미 거르고, 나머지는 코드값이거나 숫자라 임베딩이 비교하지 못한다
 * (`.ai/STATE.md` "[2][3] 임베딩 25 + 조건점수 75 / 텍스트 임베딩 재설계"). 스킬을 여기 남겨두면 <b>포지션 벡터에만
 * "Java, Spring Boot"가 있고 프리랜서 벡터엔 대응하는 말이 없어서</b>, 프리랜서가 자기소개에
 * 우연히 "자바"라고 써둔 경우에만 걸린다 — 이번 재설계의 발단이 된 버그가 정확히 그것이다.
 *
 * <p>{@code extraNote}(우대사항)는 반드시 남아 있어야 한다. 프리랜서 쪽에 학과를 넣은 이유가
 * "○○학과 우대"를 잡기 위해서라, 한쪽을 빼면 다른 쪽도 의미가 없어진다(둘은 세트다).
 *
 * <p>{@link FreelancerEmbeddingTextBuilder}와 짝을 맞춰야 한다. 한쪽에만 어떤 항목이 들어가면
 * 대조할 말이 반대쪽에 없어서 그 항목이 유사도에 사실상 반영되지 않는다.
 */
final class PositionEmbeddingTextBuilder {

    private PositionEmbeddingTextBuilder() {
    }

    static String buildText(ProjectPositionSummary summary) {
        // 값 없는 항목은 줄 자체를 뺀다. 빈 줄만 남기면 그 자리에 아무 의미 없는 토큰이 들어가
        // 항목 수가 적은 프로젝트끼리 서로 비슷해 보이는 쪽으로 벡터가 밀린다.
        return Stream.of(
                        summary.projectTitle(),
                        summary.currentSituation(),
                        summary.mainTask(),
                        summary.detailScope(),
                        summary.extraNote())
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
