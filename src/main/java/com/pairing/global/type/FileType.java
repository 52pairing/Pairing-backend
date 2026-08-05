package com.pairing.global.type;

/**
 * 프로젝트 전체에서 공통으로 사용하는 미디어 파일 타입 규격.
 * 판별은 {@code global/util/FileTypeDetector}가 담당한다.
 */
public enum FileType {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    UNKNOWN
}
