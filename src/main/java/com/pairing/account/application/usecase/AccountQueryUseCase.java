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

    /** 클라이언트 마이페이지(기업정보) 조회용. 없으면 {@code AC_002}. */
    ClientProfile getClientProfile(Long accountId);

    /** 매칭/협상 도메인이 freelancer_profile.id 로 프리랜서를 다시 찾을 때 쓴다. 없으면 empty. */
    Optional<FreelancerProfile> findFreelancerProfileById(Long freelancerProfileId);

    /** 매칭 도메인이 로그인 계정(accountId)을 freelancerId 로 변환할 때 쓴다. 없으면 empty. */
    Optional<FreelancerProfile> findFreelancerProfileByAccountId(Long accountId);

    /** 매칭/협상 도메인이 client_profile.id 로 클라이언트를 다시 찾을 때 쓴다. 없으면 empty. */
    Optional<ClientProfile> findClientProfileById(Long clientProfileId);

    /**
     * 주어진 계정 중 매칭 대상이 되는 프리랜서만 골라낸다.
     *
     * <p>기준은 <b>활성 계정 + AI 매칭 동의</b>다. 가입 미완료·정지·탈퇴는 전부 제외된다.
     * 탈퇴 판정은 {@code account.status} 가 정본이라 프로필의 삭제 시각은 보지 않는다.
     *
     * <p>프로젝트 사전 검수(정책 P02)가 후보 수를 셀 때 쓴다. 계정마다 되물으면 N+1 이라
     * 목록을 한 번에 받는다. 입력에 없던 id 는 결과에도 없다. 개수만 필요하면 {@code size()} 를 쓴다.
     */
    List<Long> filterActiveAiMatchingAgreed(Collection<Long> accountIds);

    /**
     * 내 결제수단. 가입 시 만들어진 카드 1건과 계좌 1건이 함께 나온다.
     *
     * <p>삭제된 건은 제외한다. 수수료 결제 화면은 {@code methodType == CARD} 만 골라 쓴다.
     */
    List<PaymentMethod> findMyPaymentMethods(Long accountId);

}
