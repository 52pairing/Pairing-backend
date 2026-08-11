package com.pairing.contract.application.command;

/**
 * 계약 서명 입력.
 *
 * <p>자체 전자서명이라 외부 인증 기관을 거치지 않는다. 로그인 세션으로 본인을 확인하고,
 * 증거는 서명 시각과 접속 정보로 남긴다. 분쟁 시 "누가 언제 어디서 눌렀는지" 를 다투기 때문이다.
 *
 * <p>{@code signatureFileId} 는 화면에서 그린 서명 이미지다. 선택이라 null 일 수 있고,
 * 그때는 동의 클릭만으로 서명 처리한다. 있으면 그림까지 증거로 남아 분쟁에서 더 유리하다.
 *
 * <p>{@code ipAddress} / {@code userAgent} 는 컨트롤러가 요청 헤더에서 뽑아 넣는다.
 * 프록시 뒤라 값이 없을 수 있어 null 을 허용한다.
 */
public record SignContractCommand(
        Long contractId,
        Long accountId,
        Long signatureFileId,
        String ipAddress,
        String userAgent
) {
}
