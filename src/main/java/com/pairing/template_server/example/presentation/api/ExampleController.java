package com.pairing.template_server.example.presentation.api;

import com.pairing.template_server.example.application.command.CreateExampleCommand;
import com.pairing.template_server.example.application.command.UploadExampleImageCommand;
import com.pairing.template_server.example.application.usecase.ExampleCommandUseCase;
import com.pairing.template_server.example.exception.ExampleErrorCode;
import com.pairing.template_server.example.presentation.api.request.CreateExampleRequest;
import com.pairing.template_server.example.presentation.api.response.ExampleResponse;
import com.pairing.template_server.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.template_server.global.common.api.response.ApiResponse;
import com.pairing.template_server.global.exception.GlobalErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/examples")
@RequiredArgsConstructor
@Tag(name = "Example", description = "예시 도메인 API")
public class ExampleController {

    private final ExampleCommandUseCase exampleCommandUseCase;

    @PostMapping
    @Operation(summary = "예시 데이터 생성", description = "새로운 예시 데이터를 생성합니다.")

    // 중첩(Repeatable) 활용:
    // 1) @Valid 껍데기 검증 실패 시 (정적 에러 - 예: 공백 입력)
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    // 2) UseCase 비즈니스 로직 검증 실패 시 (동적 에러 - 예: 정책 위반 이름 입력)
    @ApiErrorCodeExample(domain = ExampleErrorCode.class, value = {"INVALID_EXAMPLE_NAME"})
    public ResponseEntity<ApiResponse<ExampleResponse>> createExample(
            @Valid @RequestBody CreateExampleRequest request
    ) {
        CreateExampleCommand command = new CreateExampleCommand(request.name());
        Long createdId = exampleCommandUseCase.handle(command);
        ExampleResponse responseData = ExampleResponse.of(createdId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("EXAMPLE_CREATED", "예시 생성에 성공했습니다.", responseData));
    }

    @GetMapping("/{exampleId}")
    @Operation(summary = "예시 데이터 단건 조회", description = "ID로 예시 데이터를 조회합니다.")
    @ApiErrorCodeExample(domain = ExampleErrorCode.class, value = {"EXAMPLE_NOT_FOUND"})
    public ResponseEntity<ApiResponse<ExampleResponse>> getExample(
            @PathVariable Long exampleId
    ) {
        ExampleResponse responseData = ExampleResponse.of(exampleId);

        return ResponseEntity.ok(ApiResponse.success(
                "EXAMPLE_FOUND",
                "조회에 성공했습니다.",
                responseData
        ));
    }

    /**
     * 파일 업로드 예시.
     * multipart/form-data로 받고, 응답의 imageUrl은 CdnMappable에 의해 절대 URL로 변환된다.
     */
    @PostMapping(value = "/{exampleId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "예시 이미지 업로드", description = "이미지를 스토리지에 업로드하고 예시 데이터에 연결합니다.")
    @ApiErrorCodeExample(domain = ExampleErrorCode.class, value = {"EXAMPLE_NOT_FOUND"})
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_FILE_TYPE", "FILE_UPLOAD_FAILED"})
    public ResponseEntity<ApiResponse<ExampleResponse>> uploadExampleImage(
            @PathVariable Long exampleId,
            @RequestPart("image") MultipartFile image
    ) {
        UploadExampleImageCommand command = new UploadExampleImageCommand(exampleId, image);
        String uploadedKey = exampleCommandUseCase.handle(command);

        // DB/도메인은 object key만 다루고, 절대 URL 조립은 응답 직렬화 시점에 일어난다.
        ExampleResponse responseData = new ExampleResponse(exampleId, uploadedKey);

        return ResponseEntity.ok(ApiResponse.success(
                "EXAMPLE_IMAGE_UPLOADED",
                "이미지 업로드에 성공했습니다.",
                responseData
        ));
    }
}
