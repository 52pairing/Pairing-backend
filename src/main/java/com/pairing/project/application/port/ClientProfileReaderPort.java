package com.pairing.project.application.port;

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

    /** 프로젝트가 필요로 하는 클라이언트 최소 조회 모델. */
    record ClientProfileView(
            Long clientProfileId,
            String address
    ) {
    }
}