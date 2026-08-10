package com.pairing.chat.application.usecase;

/**
 * 채팅 입력 활성화 인바운드 포트. <b>계약 도메인이 계약 체결 시 호출한다.</b>
 *
 * <p>협상이 타결되면 채팅방은 만들어지지만 입력창은 잠겨 있다(합의안만 보인다). 계약이 체결돼야
 * 프로젝트 진행 대화를 시작할 수 있으므로, 그 시점을 아는 계약 도메인이 이 포트로 열어 준다.
 *
 * <p>채팅 명령 전체({@link ChatCommandUseCase})가 아니라 이 메서드만 열어 두는 이유는, 계약 도메인이
 * 메시지 전송·나가기 같은 다른 명령까지 알 필요가 없기 때문이다.
 *
 * <p><b>주의: 이 포트를 부르는 곳이 아직 없다.</b> 계약 도메인의 서비스 계층이 구현되면 체결 처리
 * 끝에 {@code chatActivationUseCase.enableInputForSignedContract(negotiationId)} 한 줄을 넣으면 된다.
 * 그때까지는 타결 후에도 채팅 입력이 열리지 않는다.
 */
public interface ChatActivationUseCase {

    /**
     * 계약이 체결된 협상의 채팅 입력창을 연다. 이미 열려 있으면 아무 일도 하지 않는다(멱등).
     * 처음 열릴 때만 안내 시스템 메시지를 남긴다.
     *
     * @param negotiationId 계약의 근거가 된 협상 ID. 방은 협상 1건당 하나다
     */
    void enableInputForSignedContract(Long negotiationId);
}
