package com.pairing.contract.application.command;

/**
 * 계약 서명 입력.
 *
 * <p>자체 전자서명이라 서명 이미지를 받지 않는다. 화면은 확인 모달 하나이고,
 * 증거는 서명 시각과 접속 정보로 남긴다. 분쟁 시 "누가 언제 어디서 눌렀는지" 를 다투기 때문이다.
 *
 * <p>{@code ipAddress} / {@code userAgent} 는 컨트롤러가 요청 헤더에서 뽑아 넣는다.
 * 프록시 뒤라 값이 없을 수 있어 null 을 허용한다.
 */
public record SignContractCommand(
        Long contractId,
        Long accountId,
        String ipAddress,
        String userAgent
) {
}
