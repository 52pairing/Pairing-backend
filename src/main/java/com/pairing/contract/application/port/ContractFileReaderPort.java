package com.pairing.contract.application.port;

/**
 * 서명 이미지 확인. file 도메인을 계약이 직접 참조하지 않으려고 둔다.
 *
 * <p>서명은 5년 남는 증거다. 없는 파일을 가리킨 채로 체결되면 나중에 계약서를 다시 그릴 때
 * 서명란이 빈다. 그 시점에는 되돌릴 수 없으므로 서명 시점에 존재를 확인한다.
 */
public interface ContractFileReaderPort {

    /** 업로드된 파일이 남아 있는가. */
    boolean exists(Long fileId);

    /** 화면·PDF 에 넣을 이미지 주소. 없으면 null. */
    String findUrl(Long fileId);
}
