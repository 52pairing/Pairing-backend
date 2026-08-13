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

    /**
     * 개인정보 보관 기한이 지난 탈퇴 계정. 파기 배치가 쓴다.
     *
     * <p>한 번에 다 가져오지 않고 나눠서 처리한다. 1년 전 탈퇴자가 한꺼번에 몰려 있으면
     * 배치 한 번이 그 전부를 메모리에 올린다.
     */
    List<Account> findPurgeTargets(LocalDateTime now, int limit);

    /**
     * 활성 계정 id. {@code afterId} 보다 큰 것만 id 오름차순으로 돌려준다.
     *
     * <p>등급 산정 배치가 전체 회원을 훑는 데 쓴다. 계정 전체를 한 번에 올리면 회원이 늘수록
     * 배치 한 번이 메모리를 통째로 잡는다. 마지막 id 를 들고 이어서 읽는다.
     */
    List<Long> findActiveIdsByRole(Role role, Long afterId, int limit);
}
