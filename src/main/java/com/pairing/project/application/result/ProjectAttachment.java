package com.pairing.project.application.result;

/**
 * 첨부 자료 다운로드 한 벌. 바이트와 내려받을 파일명이다.
 *
 * <p>목록·상세에 실리는 {@code ProjectFileView} 와 나눠 둔 이유는 담는 것이 다르기 때문이다.
 * 그쪽은 화면에 그릴 메타(이름·크기·CDN 경로)이고, 이건 실제 내용이다. 한 타입으로 합치면
 * 목록 조회 10건마다 S3 를 10번 읽게 된다.
 */
public record ProjectAttachment(byte[] content, String originalName) {
}
