package com.pairing.chat.application.usecase;

/**
 * 1:1 채팅방 개설 인바운드 포트. <b>계약 도메인이 계약 체결 시 호출한다.</b>
 *
 * <p>프로젝트 진행 대화는 계약이 체결된 뒤에 시작하므로, 방도 그때 만든다. 협상이 타결되기만 하고
 * 계약이 무산되면 방이 아예 생기지 않는다(빈 방이 남지 않는다).
 *
 * <p>채팅 명령 전체({@link ChatCommandUseCase})가 아니라 이 메서드만 열어 두는 이유는, 계약 도메인이
 * 메시지 전송·나가기 같은 다른 명령까지 알 필요가 없기 때문이다.
 *
 * <p>호출부는 계약 도메인의 {@code ContractChatListener} 다. 체결 이벤트를 <b>커밋 후 새 트랜잭션</b>
 * 으로 받아 부르므로, 방 개설이 실패해도 서명은 되돌아가지 않는다.
 *
 * <p>그 개설이 실패한 건은 채팅 조회 시점에 {@code ChatRoomRepairer} 가 같은 메서드로 되살린다.
 * <b>체결 여부는 부르는 쪽이 확인한다</b> — 이 메서드는 "체결됐다"를 전제로 방만 만든다.
 */
public interface ChatActivationUseCase {

    /**
     * 계약이 체결된 협상의 1:1 채팅방을 연다. 이미 있으면 아무 일도 하지 않는다(멱등).
     *
     * @param negotiationId 계약의 근거가 된 협상 ID. 방은 협상 1건당 하나다(negotiation_id UNIQUE)
     */
    void openForSignedContract(Long negotiationId);
}
