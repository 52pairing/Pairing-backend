package com.pairing.file.presentation.api;

import com.pairing.file.domain.model.FilePurpose;
import com.pairing.file.presentation.api.response.FileResponse;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공통 파일 업로드.
 *
 * <p>파일을 먼저 올려 fileId 를 받고, 각 도메인 API 는 요청 본문에 fileId 만 담는다.
 * 등록 폼이 여러 화면으로 나뉘어 있어(프로젝트 4화면, 이력서 2화면) 임시 저장이 쉬워지고,
 * 재시도 시 파일을 다시 올리지 않아도 된다.
 *
 * <p>용도별 제한: 프로필/로고 5MB(이미지), 포트폴리오 100MB(PDF), 프로젝트 자료 100MB(프로젝트당 10개)
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "04. File", description = "공통 파일 업로드 API")
public class FileController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "파일 업로드", description = "purpose 에 따라 허용 확장자와 크기 상한이 달라집니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_FILE_TYPE", "FILE_UPLOAD_FAILED"})
    public ResponseEntity<ApiResponse<FileResponse>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam FilePurpose purpose,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 파일 저장 및 file 테이블 등록
        FileResponse data = new FileResponse(1L, "portfolio.pdf", "files/portfolio/uuid.pdf",
                "application/pdf", 1_048_576L);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("FILE_UPLOADED", "업로드에 성공했습니다.", data));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "파일 메타 조회", description = "다운로드 URL 을 얻을 때 사용합니다.")
    public ResponseEntity<ApiResponse<FileResponse>> findOne(
            @PathVariable Long fileId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 조회 및 접근 권한 확인
        FileResponse data = new FileResponse(fileId, "portfolio.pdf", "files/portfolio/uuid.pdf",
                "application/pdf", 1_048_576L);

        return ResponseEntity.ok(ApiResponse.success("FILE_FOUND", "조회에 성공했습니다.", data));
    }

    @DeleteMapping("/{fileId}")
    @Operation(summary = "파일 삭제", description = "업로드한 본인만 삭제할 수 있습니다.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long fileId,
            @CurrentAccountId Long accountId
    ) {
        // TODO: 소유자 확인 후 삭제
        return ResponseEntity.ok(ApiResponse.success("FILE_DELETED", "삭제되었습니다."));
    }
}
