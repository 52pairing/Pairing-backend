package com.pairing.contract.infrastructure;

import com.pairing.contract.application.port.ContractFileReaderPort;
import com.pairing.file.application.usecase.FileQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ContractFileReaderPort 구현. file 도메인의 인바운드 포트를 호출한다.
 *
 * <p>{@code findObjectKey} 는 없는 파일에 예외 대신 empty 를 돌려준다. 다른 도메인이 자기 응답을
 * 조립할 때 쓰라고 만들어진 조회라, 계약서 서명란이 비는 것과 계약 조회가 막히는 것을 구분할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ContractFileReaderAdapter implements ContractFileReaderPort {

    private final FileQueryUseCase fileQueryUseCase;

    @Override
    public boolean exists(Long fileId) {
        return fileId != null && fileQueryUseCase.findObjectKey(fileId).isPresent();
    }

    @Override
    public String findUrl(Long fileId) {
        return fileId == null ? null : fileQueryUseCase.findObjectKey(fileId).orElse(null);
    }
}
