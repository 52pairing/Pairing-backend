package com.pairing.template_server.example.application.usecase;

import com.pairing.template_server.example.application.command.CreateExampleCommand;
import com.pairing.template_server.example.application.command.UploadExampleImageCommand;

// 외부 계층(Controller)이 바라보는 인터페이스
public interface ExampleCommandUseCase {

    Long handle(CreateExampleCommand command);

    /** 이미지를 업로드하고 저장된 object key(상대경로)를 반환한다. */
    String handle(UploadExampleImageCommand command);
}
