package com.pairing.file.application.command;

import com.pairing.file.domain.model.FilePurpose;

/**
 * 서버가 만들어 낸 파일 저장 입력.
 *
 * <p>{@link UploadFileCommand} 는 {@code MultipartFile} 을 받아 HTTP 업로드 전제다. 계약서 PDF 처럼
 * 서버가 그 자리에서 만든 바이트는 그 경로로 넣을 수 없다. 임시 파일로 떨어뜨렸다 지우는 방법도
 * 있지만, 실패하면 찌꺼기가 남아 지우는 책임이 생긴다.
 *
 * <p>업로더({@code ownerAccountId})는 저장을 유발한 사람이다. 계약서라면 마지막으로 서명해
 * 체결시킨 당사자가 된다. 삭제 권한 판정에 쓰인다.
 *
 * @param originalName 사용자에게 보일 이름. 화면 표시와 다운로드 파일명에 쓴다
 * @param contentType  MIME. 스토리지가 응답 헤더에 그대로 싣는다
 */
public record UploadGeneratedFileCommand(
        Long ownerAccountId,
        FilePurpose purpose,
        byte[] content,
        String originalName,
        String contentType
) {
}
