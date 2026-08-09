package com.pairing.project.application.result;

import com.pairing.project.domain.model.Project;

/**
 * 목록 카드 한 장.
 *
 * <p>{@code payableSettlementId} 는 정산 도메인 값이라 애그리거트에 담지 않고 조회 시점에 합친다.
 * 카드의 결제 버튼이 이 값으로 열리고 닫힌다.
 *
 * <p>직무·스킬 라벨과 기간 표기는 담지 않는다. 화면 표기라 응답을 조립하는 쪽이 만든다.
 */
public record ProjectSummary(Project project, Long payableSettlementId) {
}
