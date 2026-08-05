package com.pairing.template_server.example.domain.model;

import com.pairing.template_server.example.exception.ExampleErrorCode;
import com.pairing.template_server.global.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Example {

    private Long id;
    private String name;
    private boolean active;

    // S3 object key(상대경로)를 담는다. 절대 URL 변환은 응답 직렬화 시점에 CdnUrlSerializer가 처리하므로
    // 도메인/DB는 CDN 주소를 모른다. 필드명이 'Url'로 끝나야 자동 변환 대상이 된다.
    private String imageUrl;

    // 1. 생성 시점의 검증
    private Example(String name) {
        validateName(name); // 객체 생성 시 무조건 검증을 통과해야 함
        this.name = name;
        this.active = true;
    }

    private Example(Long id, String name, boolean active, String imageUrl) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.imageUrl = imageUrl;
    }

    public static Example create(String name) {
        return new Example(name);
    }

    public static Example reconstitute(Long id, String name, boolean active, String imageUrl) {
        return new Example(id, name, active, imageUrl);
    }

    // 2. 비즈니스 로직 실행(상태 변경) 시점의 검증
    public void changeName(String newName) {
        validateName(newName); // 이름 변경 시에도 동일한 도메인 규칙 적용
        this.name = newName;
    }

    /** 업로드된 파일의 object key로 이미지를 교체하고, 교체 전 key를 반환한다(호출자가 기존 파일을 삭제할 수 있게). */
    public String changeImage(String newImageKey) {
        String previousKey = this.imageUrl;
        this.imageUrl = newImageKey;
        return previousKey;
    }

    public void deactivate() {
        if (this.name.startsWith("NEW_")) {
            // 특정 비즈니스 상태에 따른 에러 발생 예시
            throw new BusinessException(ExampleErrorCode.CANNOT_DEACTIVATE_NEW_EXAMPLE);
        }
        this.active = false;
    }

    // 3. 도메인 규칙(Invariant) 검증 메서드
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ExampleErrorCode.INVALID_EXAMPLE_NAME);
        }
        if (name.length() < 2 || name.length() > 50) {
            throw new BusinessException(ExampleErrorCode.NAME_LENGTH_EXCEEDED);
        }
    }
}