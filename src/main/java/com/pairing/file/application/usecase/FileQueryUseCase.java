package com.pairing.file.application.usecase;

import com.pairing.file.application.result.FileResult;

import java.util.Optional;

public interface FileQueryUseCase {

    /** 없으면 {@code FI_001}. */
    FileResult getById(Long fileId);

    /**
     * 다른 도메인이 자기 응답을 조립할 때 fileId 로 object key 만 가져오는 용도.
     * fileId 가 null 이거나 대상이 없으면 empty (다른 도메인은 이걸 profileImageUrl 등에 null 로 반영하면 된다).
     */
    Optional<String> findObjectKey(Long fileId);

    /**
     * 파일 내용을 바이트로 읽는다. 없거나 스토리지에서 못 읽으면 empty.
     *
     * <p>CDN 직링크로 못 내보내는 파일에 쓴다. 계약서는 당사자만 열람할 수 있어야 해서 서버가
     * 권한을 확인한 뒤 바이트를 실어 보낸다.
     *
     * <p><b>권한 검사를 하지 않는다.</b> 누가 볼 수 있는지는 그 파일을 참조하는 도메인이 안다.
     * 부르는 쪽이 먼저 확인해야 한다.
     */
    Optional<byte[]> readContent(Long fileId);

    /**
     * 이 계정이 올린 파일이 맞는지. 다른 도메인이 요청으로 받은 fileId 를 저장하기 전에 확인하는 용도다.
     *
     * <p>"없다"와 "남의 것이다"를 구분하지 않고 한 값으로 돌려준다. 구분해서 알려주면 fileId 를
     * 바꿔가며 호출해 남의 파일이 존재하는지 알아낼 수 있다.
     *
     * <p>fileId 가 null 이면 false 다. 첨부가 없는 경우는 호출하는 쪽이 목록을 비워 처리한다.
     */
    boolean isOwnedBy(Long fileId, Long accountId);
}
