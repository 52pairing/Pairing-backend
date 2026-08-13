package com.pairing.project.application.port;

import java.util.List;
import java.util.Optional;

/**
 * 첨부 자료 메타 조회. file 도메인 구현을 감싼다.
 *
 * <p>프로젝트는 fileId 만 저장하고 파일명·크기·경로는 매번 file 도메인에서 읽는다.
 * 파일 이름이 바뀌거나 지워져도 프로젝트가 옛 값을 들고 있지 않게 하기 위해서다.
 */
public interface ProjectFileReaderPort {

    /**
     * fileId 목록을 요청한 순서 그대로 돌려준다. 하나라도 없으면 {@code FI_001} 이 올라온다.
     *
     * <p>등록 시 검증도 이 메서드로 한다. "읽을 수 있으면 존재한다" 가 같은 뜻이라 경로를 나누지 않았다.
     */
    List<ProjectFileView> getAllByIds(List<Long> fileIds);

    /**
     * 첨부 원본 바이트. 없거나 스토리지에서 못 읽으면 empty.
     *
     * <p>메타의 {@code objectKey} 는 CDN 직링크라 인증이 없다. 그 URL 을 아는 사람은 누구든,
     * 로그아웃 상태로도, 기한 없이 받을 수 있다. 다운로드를 서버가 중계하려고 이 경로를 둔다 —
     * 열람 권한을 확인한 뒤 바이트를 실어 보낸다. 계약서 PDF 와 같은 방식이다.
     *
     * <p><b>권한 검사를 하지 않는다.</b> 부르는 쪽이 먼저 확인해야 한다.
     */
    Optional<byte[]> readContent(Long fileId);

    /** {@code objectKey} 는 상대경로다. 응답 DTO 의 {@code ~Url} 필드에 담으면 CDN 절대 URL 로 바뀐다. */
    record ProjectFileView(Long fileId, String originalName, Long sizeBytes, String objectKey) {
    }
}
