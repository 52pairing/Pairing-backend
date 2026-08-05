package com.pairing.account.domain.repository;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 계정 리포지토리 포트.
 *
 * <p>이메일/휴대폰은 역할별로 유니크다. 한 사람이 클라이언트 계정과 프리랜서 계정을 각각 가질 수 있어
 * 조회와 중복 판정에 항상 역할이 함께 들어간다.
 */
public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(Long id);

    /** 로그인 아이디는 이메일이지만, 역할까지 맞아야 계정 하나로 좁혀진다. */
    Optional<Account> findByEmailAndRole(String email, Role role);

    boolean existsByEmailAndRole(String email, Role role);

    boolean existsByPhoneAndRole(String phone, Role role);

    /** 아이디 찾기. 같은 사람이 두 역할로 가입했다면 둘 다 나온다. */
    List<Account> findAllByNameAndPhone(String name, String phone);

    /** 비밀번호 찾기. 역할 + 이메일 + 이름 + 전화번호가 모두 일치해야 한다. */
    Optional<Account> findByEmailAndRoleAndNameAndPhone(String email, Role role, String name, String phone);

    /** 탈퇴 후 재가입 제한(30일)에 걸리는 이력이 있는지 확인한다. 역할별로 본다. */
    boolean existsRejoinRestrictedByEmailHash(String emailHash, Role role, LocalDateTime now);

    boolean existsRejoinRestrictedByPhoneHash(String phoneHash, Role role, LocalDateTime now);
}
