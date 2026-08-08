package com.pairing.project.application.port;

import java.util.List;

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

    /** {@code objectKey} 는 상대경로다. 응답 DTO 의 {@code ~Url} 필드에 담으면 CDN 절대 URL 로 바뀐다. */
    record ProjectFileView(Long fileId, String originalName, Long sizeBytes, String objectKey) {
    }
}
