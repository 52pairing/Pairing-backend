package com.pairing.contract.application.port;

import java.util.Optional;

/**
 * 체결된 계약서 PDF 를 파일로 굳히고 다시 읽는다.
 *
 * <p><b>왜 굳히나.</b> 지금 계약서는 요청할 때마다 다시 그린다. 그러면 조항 문구나 표기 규칙을
 * 고쳤을 때 <b>이미 체결된 계약서까지 새 양식으로 바뀐다.</b> 계약은 5년 보관 대상이라 그때 그
 * 문서가 그대로 남아야 한다.
 *
 * <p>정산 계좌 동결({@code contract.settlement_account_enc})이 데이터가 바뀌는 것을 막는다면,
 * 이쪽은 <b>양식이 바뀌는 것</b>을 막는다. 둘은 막는 대상이 다르다.
 *
 * <p>읽기가 필요한 이유는 계약서를 CDN 직링크로 못 내보내기 때문이다. 당사자만 열람할 수 있어야
 * 해서 서버가 권한을 확인한 뒤 바이트를 실어 보낸다.
 */
public interface ContractArchivePort {

    /**
     * 계약서 PDF 를 저장하고 fileId 를 돌려준다.
     *
     * @param ownerAccountId 저장을 유발한 당사자. 체결시킨 쪽이다
     * @param contractNo     파일명에 쓴다. 화면과 다운로드에 그대로 보인다
     */
    Long archive(byte[] pdf, String contractNo, Long ownerAccountId);

    /** 굳혀둔 계약서를 읽는다. 없거나 스토리지에서 못 읽으면 empty — 부르는 쪽이 다시 그린다. */
    Optional<byte[]> read(Long fileId);
}
