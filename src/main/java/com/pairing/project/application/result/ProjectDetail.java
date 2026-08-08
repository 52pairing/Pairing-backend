package com.pairing.project.application.result;

import com.pairing.project.application.port.ProjectFileReaderPort.ProjectFileView;
import com.pairing.project.domain.model.Project;

import java.util.List;

/**
 * 상세 화면 한 벌.
 *
 * <p>첨부 메타와 정산 ID 는 다른 도메인 소유라 애그리거트에 담지 않고 조회 시점에 합친다.
 * {@code files} 는 {@code project.getFileIds()} 와 같은 순서다.
 *
 * <p>{@code payableSettlementId} 는 지금 결제해야 할 정산이다. 없으면 null 이고,
 * 화면은 이 값으로 결제 버튼을 켜고 끈다.
 */
public record ProjectDetail(Project project, List<ProjectFileView> files, Long payableSettlementId) {
}
