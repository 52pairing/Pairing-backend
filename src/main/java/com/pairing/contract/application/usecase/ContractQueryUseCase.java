package com.pairing.contract.application.usecase;

import com.pairing.contract.application.result.ContractDetail;
import com.pairing.contract.application.result.ContractSummary;
import com.pairing.contract.domain.model.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 계약 조회 인바운드 포트.
 *
 * <p>계약 당사자 두 명만 볼 수 있다. 클라이언트·프리랜서 모두 같은 API 를 쓴다.
 */
public interface ContractQueryUseCase {

    /**
     * 계약서 PDF. 당사자만 받을 수 있다.
     *
     * <p>파일로 저장하지 않고 요청할 때마다 다시 굽는다. 조항 스냅샷({@code content_json})이
     * 계약에 남아 있어 언제 그려도 같은 문서가 나오기 때문이다.
     *
     * @throws com.pairing.global.exception.BusinessException 없으면 CT_001, 당사자가 아니면 CT_002
     */
    byte[] renderPdf(Long contractId, Long accountId);

    /** 다운로드 파일명. {@code CT-2026-000001.pdf} 형식이다. */
    String pdfFileName(Long contractId, Long accountId);

    /**
     * 내 계약 목록. projectId / status 가 null 이면 그 조건을 걸지 않는다.
     *
     * <p>{@code projectId} 를 주면 그 프로젝트의 내 계약만 나온다. 프로젝트 상세의 계약 탭이 쓴다.
     */
    Page<ContractSummary> findMine(Long accountId, Long projectId, ContractStatus status,
                                   Pageable pageable);

    /** 상세 조회. 없으면 CT_001, 당사자가 아니면 CT_002. */
    ContractDetail getDetail(Long contractId, Long accountId);
}
