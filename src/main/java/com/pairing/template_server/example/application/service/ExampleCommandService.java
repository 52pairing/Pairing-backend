package com.pairing.template_server.example.application.service;

import com.pairing.template_server.example.application.command.CreateExampleCommand;
import com.pairing.template_server.example.application.command.UploadExampleImageCommand;
import com.pairing.template_server.example.application.usecase.ExampleCommandUseCase;
import com.pairing.template_server.example.domain.model.Example;
import com.pairing.template_server.example.domain.repository.ExampleRepository;
import com.pairing.template_server.example.exception.ExampleErrorCode;
import com.pairing.template_server.example.settings.ExampleStorageSettings;
import com.pairing.template_server.global.exception.BusinessException;
import com.pairing.template_server.global.exception.GlobalErrorCode;
import com.pairing.template_server.global.port.out.FileStoragePort;
import com.pairing.template_server.global.type.FileType;
import com.pairing.template_server.global.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ExampleCommandService implements ExampleCommandUseCase {

    private final ExampleRepository exampleRepository;

    // S3 SDK를 직접 알지 않고 포트에만 의존한다.
    private final FileStoragePort fileStoragePort;
    private final ExampleStorageSettings storageSettings;

    @Override
    public Long handle(CreateExampleCommand command) {
        // 비즈니스 규칙 검증 및 예외 처리
        if (command.name() == null || command.name().isBlank()) {
            throw new BusinessException(ExampleErrorCode.INVALID_EXAMPLE_NAME);
        }

        // 도메인 엔티티 생성
        Example newExample = Example.create(command.name());

        // 상태 저장 (영속성 처리)
        Example savedExample = exampleRepository.save(newExample);

        return savedExample.getId();
    }

    @Override
    public String handle(UploadExampleImageCommand command) {
        Example example = exampleRepository.findById(command.exampleId())
                .orElseThrow(() -> new BusinessException(ExampleErrorCode.EXAMPLE_NOT_FOUND));

        // 확장자만 믿지 않고 공통 판별기로 종류를 확인한다.
        if (FileTypeDetector.determineFileType(command.image()) != FileType.IMAGE) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }

        // 업로드 결과는 절대 URL이 아니라 object key(예: examples/uuid.png)다.
        String uploadedKey = fileStoragePort.uploadFile(command.image(), storageSettings.getDirectory());

        String previousKey = example.changeImage(uploadedKey);
        exampleRepository.save(example);

        // 교체된 기존 파일은 남겨두면 스토리지에 쓰레기로 쌓이므로 정리한다.
        // (삭제 실패는 어댑터에서 로깅만 하고 넘어가므로 본 트랜잭션에 영향이 없다)
        if (previousKey != null && !previousKey.equals(uploadedKey)) {
            fileStoragePort.deleteFile(previousKey);
        }

        return uploadedKey;
    }
}
