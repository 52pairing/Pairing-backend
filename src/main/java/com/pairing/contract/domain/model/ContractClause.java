package com.pairing.contract.domain.model;

/**
 * 계약서 조항 한 개. 화면과 PDF 가 같은 문장을 쓰도록 렌더링 결과를 그대로 담는다.
 *
 * <p>본문은 완성된 문자열이다. 표시 단계에서 값을 채워 넣지 않는다. 계약서는 서명된 뒤에도
 * 같은 문장이 다시 나와야 하는데, 조립을 화면에 맡기면 화면 코드가 바뀔 때 문장이 달라진다.
 */
public record ContractClause(int no, String title, String content) {
}
