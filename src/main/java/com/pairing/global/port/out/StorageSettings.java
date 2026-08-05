package com.pairing.global.port.out;

/**
 * 각 도메인의 스토리지 설정이 구현하는 공통 인터페이스.
 *
 * <p>버킷은 전역 단일 버킷({@code S3Settings})으로 통일하고,
 * 도메인 구분은 object key의 directory(prefix)로 표현한다.
 *
 * <p>구현 예시는 {@code example/settings/ExampleStorageSettings} 참고.
 */
public interface StorageSettings {
    String getDirectory();
}
