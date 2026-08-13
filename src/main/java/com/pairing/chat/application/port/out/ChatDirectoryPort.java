package com.pairing.chat.application.port.out;

import java.util.Optional;

/**
 * 채팅 표시·구성에 필요한 타 도메인 값 읽기 포트(채팅 소유, 읽기 전용).
 *
 * <p>채팅방 참여자는 {@code account.id} 인데, 협상은 당사자를 프로필 ID(freelancer_profile.id,
 * client_profile.id)로만 안다. 방을 열려면 협상→프로필→계정으로 번역해야 하므로, negotiation·
 * project·profile·account 테이블을 읽기 전용으로 직접 조회한다(협상/계정 도메인이 포트를 주지 않아
 * project·이름 조회와 동일한 방식).
 */
public interface ChatDirectoryPort {

    /** 협상의 양측 당사자·프로젝트 표시정보. 방 생성과 목록/상세 표시에 함께 쓴다. */
    Optional<NegotiationParties> findPartiesByNegotiationId(Long negotiationId);

    /** 계정 표시 정보(메시지 보낸 사람). 프리랜서=이름, 클라이언트=회사명. */
    Optional<DisplayProfile> findDisplayProfile(Long accountId);

    /**
     * 표시용 계정 정보.
     *
     * @param name     표시명
     * @param imageKey 프로필 사진 오브젝트 키. <b>등록이 선택이라 자주 null 이다</b> —
     *                 화면은 이름 첫 글자 같은 대체 표시를 준비해야 한다
     */
    record DisplayProfile(String name, String imageKey) {
    }

    /**
     * 협상 당사자 양측을 계정 기준으로 표현한 값.
     *
     * @param clientAccountId     클라이언트 계정 ID
     * @param freelancerAccountId 프리랜서 계정 ID
     * @param projectTitle        프로젝트명
     * @param clientName          클라이언트 표시명(회사명)
     * @param freelancerName      프리랜서 표시명(이름). 계정 조회 실패 시 null 일 수 있다.
     * @param clientImageKey      클라이언트 로고 오브젝트 키. 등록이 선택이라 null 이 흔하다
     * @param freelancerImageKey  프리랜서 프로필 사진 오브젝트 키. 위와 같다
     */
    record NegotiationParties(
            Long clientAccountId,
            Long freelancerAccountId,
            String projectTitle,
            String clientName,
            String freelancerName,
            String clientImageKey,
            String freelancerImageKey
    ) {
    }
}
