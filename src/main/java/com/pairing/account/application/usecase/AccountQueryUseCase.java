package com.pairing.account.application.usecase;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;

import java.util.List;
import java.util.Optional;

/**
 * 계정 조회 인바운드 포트.
 *
 * <p>이메일과 휴대폰은 역할별 유니크라 조회/중복 판정에 역할이 반드시 따라온다.
 * 같은 사람이 클라이언트와 프리랜서로 각각 가입할 수 있기 때문이다.
 */
public interface AccountQueryUseCase {

    Account getById(Long accountId);

    Optional<Account> findByEmailAndRole(String email, Role role);

    /** 아이디 찾기. 두 역할로 가입했다면 계정이 둘 다 나온다. */
    List<Account> findAllByNameAndPhone(String name, String phone);

    Optional<Account> findByEmailAndRoleAndNameAndPhone(String email, Role role, String name, String phone);

    boolean isEmailDuplicated(String email, Role role);

    boolean isPhoneDuplicated(String phone, Role role);

    /** 사업자등록번호는 클라이언트 프로필에만 있어 역할 구분이 필요 없다. */
    boolean isBusinessNoDuplicated(String businessNo);

    /** 탈퇴 후 재가입 제한(30일)에 걸리는지 확인한다. */
    boolean isRejoinRestricted(String emailHash, String phoneHash, Role role);

    Optional<SocialAccount> findSocialAccount(SocialProvider provider, String providerUid);
}
