package com.pairing.example.application.command;

import org.springframework.web.multipart.MultipartFile;

// 파일 업로드 Command. MultipartFile은 스프링 웹 타입이지만,
// 스트림을 애플리케이션 계층까지 그대로 넘기는 것이 메모리 측면에서 유리해 이 프로젝트는 그대로 받는다.
public record UploadExampleImageCommand(
        Long exampleId,
        MultipartFile image
) {
}
