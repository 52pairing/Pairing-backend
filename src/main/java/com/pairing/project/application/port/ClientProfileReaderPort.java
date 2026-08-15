package com.pairing.project.application.port;

import com.pairing.client.domain.model.ClientGrade;

/**
 * 클라이언트 프로필 읽기 포트(프로젝트 소유).
 *
 * <p>project.client_id 는 client_profile.id 다. 로그인 계정(account.id)을 그 값으로 번역하고,
 * 상주 근무일 때 쓸 주소를 함께 읽는다.
 *
 * <p>프로필이 없으면 account 도메인의 AC_002 가 그대로 올라온다.
 */
public interface ClientProfileReaderPort {

    ClientProfileView getByAccountId(Long accountId);

    /**
     * client_profile.id 를 로그인 계정(account.id)으로 번역한다. 프로필이 없으면 null.
     *
     * <p>{@code getByAccountId} 의 반대 방향이다. 프로젝트가 들고 있는 건 client_profile.id 인데,
     * 그 클라이언트에게 알림을 보내려면 계정 id 가 필요하다.
     */
    Long findAccountId(Long clientProfileId);

    /** 프로젝트가 필요로 하는 클라이언트 최소 조회 모델. grade 는 착수금 수수료 할인에 쓴다. */
    record ClientProfileView(
            Long clientProfileId,
            String address,
            ClientGrade grade
    ) {
    }
}