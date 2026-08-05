package com.pairing.template_server.example.settings;

import com.pairing.template_server.global.port.out.StorageSettings;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * example 도메인의 스토리지 설정.
 * 버킷은 전역 단일 버킷을 쓰고, 도메인 구분은 object key prefix(디렉터리)로만 한다.
 */
@Getter
@Component
public class ExampleStorageSettings implements StorageSettings {

    // object key prefix (예: examples/uuid.png)
    @Value("${example.storage.directory:examples}")
    private String directory;
}
