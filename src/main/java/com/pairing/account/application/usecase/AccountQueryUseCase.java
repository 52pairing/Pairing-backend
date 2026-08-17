package com.pairing.account.application.usecase;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.ClientProfile;
import com.pairing.account.domain.model.FreelancerProfile;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SocialAccount;
import com.pairing.account.domain.model.SocialProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 계정 조회 인바운드 포트.
 *
 * <p>이메일과 휴대폰은 역할별 유니크라 조회/중복 판정에 역할이 반드시 따라온다.
 * 같은 사람이 클라이언트와 프리랜서로 각각 가입할 수 있기 때문이다.
 */
public interface AccountQueryUseCase {

    Account getById(Long accountId);

    /**
     * 계정 조회. 없으면 empty.
     *
     * <p>계정이 사라져도 화면이 열려야 하는 쪽이 쓴다. 계약서는 5년 보관이라 계정보다 오래 남는데,
     * 이름 한 칸이 비었다고 계약서 조회가 통째로 막히면 안 된다. 없는 것이 정상 흐름인 경우만
     * 이걸 쓰고, 계정이 반드시 있어야 하는 곳은 {@link #getById} 를 그대로 쓴다.
     */
    Optional<Account> findById(Long accountId);

    /**
     * 여러 계정을 한 번에. 목록 화면이 항목마다 {@link #getById} 를 부르면 N+1 이라 만들었다.
     * 없는 id 는 결과에서 빠진다.
     */
    Map<Long, Account> getByIds(Collection<Long> accountIds);

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

    /** 클라이언트 마이페이지(기업정보) 조회용. 없으면 {@code AC_002}. */
    ClientProfile getClientProfile(Long accountId);

    /**
     * 로그인 계정(accountId)으로 클라이언트 프로필을 찾는다. 없으면 empty.
     *
     * <p>{@link #getClientProfile} 과 달리 없는 것을 예외로 보지 않는다. 프로필이 빠져도 화면은
     * 열려야 하는 쪽이 쓴다 — 예를 들어 {@code GET /auth/me} 는 로그인 상태 확인이 본업이라
     * 기업명 한 칸 때문에 로그인 직후 진입이 통째로 막히면 안 된다.
     */
    Optional<ClientProfile> findClientProfileByAccountId(Long accountId);

    /** 매칭/협상 도메인이 freelancer_profile.id 로 프리랜서를 다시 찾을 때 쓴다. 없으면 empty. */
    Optional<FreelancerProfile> findFreelancerProfileById(Long freelancerProfileId);

    /**
     * 여러 freelancer_profile.id 를 한 번에. 후보 목록이 항목마다
     * {@link #findFreelancerProfileById} 를 부르면 N+1 이라 만들었다. 없는 id 는 결과에서 빠진다.
     */
    Map<Long, FreelancerProfile> findFreelancerProfilesByIds(Collection<Long> freelancerProfileIds);

    /** 매칭 도메인이 로그인 계정(accountId)을 freelancerId 로 변환할 때 쓴다. 없으면 empty. */
    Optional<FreelancerProfile> findFreelancerProfileByAccountId(Long accountId);

    /**
     * 여러 계정의 프로필을 accountId 로 한 번에. 목록 화면이 계정마다
     * {@link #findFreelancerProfileByAccountId} 를 부르면 N+1 이라 만들었다.
     * 프로필이 없는 계정은 결과에서 빠진다.
     */
    Map<Long, FreelancerProfile> findFreelancerProfilesByAccountIds(Collection<Long> accountIds);

    /** 매칭/협상 도메인이 client_profile.id 로 클라이언트를 다시 찾을 때 쓴다. 없으면 empty. */
    Optional<ClientProfile> findClientProfileById(Long clientProfileId);

    /**
     * 주어진 계정 중 매칭 대상이 되는 프리랜서만 골라낸다.
     *
     * <p>기준은 <b>활성 계정 + AI 매칭 동의 + 매칭 일시중지 아님</b>다. 가입 미완료·정지·탈퇴는 전부 제외된다.
     * 탈퇴 판정은 {@code account.status} 가 정본이라 프로필의 삭제 시각은 보지 않는다.
     *
     * <p>프로젝트 사전 검수(정책 P02)가 후보 수를 셀 때 쓴다. 계정마다 되물으면 N+1 이라
     * 목록을 한 번에 받는다. 입력에 없던 id 는 결과에도 없다. 개수만 필요하면 {@code size()} 를 쓴다.
     */
    List<Long> filterActiveAiMatchingAgreed(Collection<Long> accountIds);

    /**
     * 등급 산정 대상 계정 id. 활성 계정만, id 오름차순.
     *
     * <p>전체를 한 번에 올리지 않고 {@code afterId} 로 이어서 읽는다. 회원이 늘어도 배치 한 번이
     * 메모리를 통째로 잡지 않는다.
     *
     * @param afterId 이 id 보다 큰 것만. 첫 페이지는 0
     */
    List<Long> findActiveAccountIdsByRole(Role role, Long afterId, int limit);

    /**
     * 내 결제수단. 가입 시 만들어진 카드 1건과 계좌 1건이 함께 나온다.
     *
     * <p>삭제된 건은 제외한다. 수수료 결제 화면은 {@code methodType == CARD} 만 골라 쓴다.
     */
    List<PaymentMethod> findMyPaymentMethods(Long accountId);

}
