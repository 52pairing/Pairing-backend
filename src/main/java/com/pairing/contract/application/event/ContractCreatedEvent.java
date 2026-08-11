package com.pairing.contract.application.event;

import java.util.List;

/**
 * 계약서가 DRAFT 로 만들어졌다. 본문의 자유 텍스트를 채워 서명 대기로 넘겨야 한다.
 *
 * <p>협상 타결 트랜잭션 안에서 발행하고 <b>커밋 후에</b> 처리한다. 문구 정리는 AI 서버를 부르는데,
 * 그걸 기다리면 사용자가 협상 화면에서 응답을 기다리게 된다.
 *
 * @param contractId  대상 계약
 * @param projectId   업무 원문(mainTask·detailScope)을 읽어 올 프로젝트
 * @param agreedNotes 협상 SCOPE·OTHER 합의값. 특약사항 후보. 비어 있으면 특약 없음으로 확정된다
 */
public record ContractCreatedEvent(Long contractId, Long projectId, List<String> agreedNotes) {
}
