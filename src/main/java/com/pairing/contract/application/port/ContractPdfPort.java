package com.pairing.contract.application.port;

import com.pairing.contract.application.result.ContractPdfView;

/**
 * 계약서 PDF 생성. 템플릿 엔진과 PDF 라이브러리를 응용 계층에서 감춘다.
 *
 * <p>실패는 예외로 알린다. AI 문구와 달리 대체할 기본값이 없다 — 문서가 안 나오면 다운로드 자체가
 * 성립하지 않으므로 조용히 넘어가면 사용자가 빈 파일을 받는다.
 */
public interface ContractPdfPort {

    byte[] render(ContractPdfView view);
}
