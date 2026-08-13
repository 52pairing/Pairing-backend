package com.pairing.contract.application.port;

import com.pairing.account.domain.model.BusinessField;

import java.util.Optional;

/**
 * 계약 당사자 정보. 계약서 머리말의 갑·을 표시에 쓴다.
 *
 * <p>계약이 들고 있는 건 {@code client_profile.id} 와 {@code freelancer_profile.id} 라
 * account 도메인에 한 번 더 물어야 이름·연락처·계좌가 나온다. 그 변환을 어댑터가 감춘다.
 *
 * <p>이름만 필요한 목록 화면을 위해 가벼운 조회를 따로 둔다. 상세 조회는 계좌 복호화까지 하는데,
 * 목록 10건마다 그걸 돌리면 쓰지도 않을 계좌번호가 응답에 실린다.
 */
public interface ContractPartyReaderPort {

    /** 목록 카드용. 클라이언트는 기업명을 쓴다. 없으면 null. */
    String findClientName(Long clientProfileId);

    /**
     * 목록 카드용. 기업명과 업종을 함께 준다. 없으면 {@link ClientSummary#EMPTY}.
     *
     * <p>프리랜서 화면 카드가 "주식회사 페어링 · IT/컨텐츠/AI" 로 찍는다. 기업명만 필요한 곳은
     * {@link #findClientName} 을 그대로 쓴다.
     */
    ClientSummary findClientSummary(Long clientProfileId);

    /** 목록 카드에 찍는 갑 요약. */
    record ClientSummary(String companyName, BusinessField businessField) {

        public static final ClientSummary EMPTY = new ClientSummary(null, null);
    }

    /** 목록 카드용. 프리랜서는 계정 이름을 쓴다. 없으면 null. */
    String findFreelancerName(Long freelancerProfileId);

    /** 갑. 없으면 {@link ClientParty#EMPTY}. */
    ClientParty findClient(Long clientProfileId);

    /** 을. 없으면 {@link FreelancerParty#EMPTY}. */
    FreelancerParty findFreelancer(Long freelancerProfileId);

    /**
     * 체결 시점 정산 계좌를 <b>암호문 한 줄</b>로 뽑는다. 계좌가 없으면 empty.
     *
     * <p>계약서 제5조에 찍히는 표기("카카오뱅크 3333012345678 (예금주: 김민준)")를 그대로 굳힌다.
     * 은행·번호·예금주를 따로 담지 않는 이유는, 계약서에 남아야 하는 것이 <b>그때 문서에 적혔던
     * 그 한 줄</b>이기 때문이다. 나중에 은행 표기 규칙이 바뀌어도 문서는 안 바뀐다.
     *
     * <p>평문을 계약 테이블에 복사하면 계좌번호 평문이 한 벌 더 생긴다. {@code payment_method} 가
     * 암호문만 두는 것과 같은 기준으로 여기서 암호화해 넘긴다.
     */
    Optional<byte[]> settlementAccountSnapshot(Long freelancerProfileId);

    /** 굳혀둔 정산 계좌를 복호화한다. null 이면 null. */
    String restoreSettlementAccount(byte[] snapshot);

    /**
     * 계약서의 갑.
     *
     * <p>{@code representative} 는 계정 이름이다. 클라이언트 계정의 이름이 곧 대표자명이고
     * 수정할 수 없다({@code account.name} 스키마 주석).
     *
     * @param accountId 로그인 계정. 서명 주체를 만들 때 쓴다
     */
    record ClientParty(Long accountId, String companyName, String businessNo,
                       String representative, String address, String phone) {

        /** 프로필이 지워졌을 때. 계약은 5년 보관이라 원본보다 오래 남는다. */
        public static final ClientParty EMPTY = new ClientParty(null, null, null, null, null, null);
    }

    /**
     * 계약서의 을.
     *
     * <p>정산 계좌는 <b>조회 시점 값</b>이다. 계약 테이블에 계좌 칸이 없어 동결하지 못한다.
     * 프리랜서가 마이페이지에서 계좌를 바꾸면 이미 체결된 계약서의 표시도 따라 바뀐다.
     * PDF 를 만들 때 그 시점 값을 파일에 굳혀 두는 것으로 해결할 예정이다.
     *
     * <p>{@code accountNo} 는 복호화된 평문이다. 계약 당사자 둘만 볼 수 있는 문서라 전체를 적는다.
     */
    record FreelancerParty(Long accountId, String name, String phone,
                           String bankName, String accountNo, String accountHolder,
                           String frozenAccount) {

        public static final FreelancerParty EMPTY = new FreelancerParty(null, null, null, null, null, null);

        /**
         * 굳혀둔 계좌 없이 만든다. 체결 전 계약과 목록 조회가 쓴다.
         *
         * <p>기존 형태를 그대로 남겨 이 레코드를 만드는 다른 곳이 바뀌지 않게 한다.
         */
        public FreelancerParty(Long accountId, String name, String phone,
                               String bankName, String accountNo, String accountHolder) {
            this(accountId, name, phone, bankName, accountNo, accountHolder, null);
        }

        /** 체결 시점에 굳혀둔 계좌를 얹은 사본. null 이면 그대로 둔다. */
        public FreelancerParty withFrozenAccount(String frozen) {
            return frozen == null ? this
                    : new FreelancerParty(accountId, name, phone, bankName, accountNo, accountHolder, frozen);
        }

        /**
         * 계약서 제5조와 을 표시에 찍는 한 줄. 계좌가 없으면 null.
         *
         * <p>체결 시점에 굳혀둔 값이 있으면 <b>그것을 쓴다.</b> 프리랜서가 나중에 계좌를 바꿔도
         * 이미 체결된 계약서의 표시는 그대로여야 한다.
         */
        public String settlementAccount() {
            if (frozenAccount != null) {
                return frozenAccount;
            }
            if (bankName == null || accountNo == null) {
                return null;
            }
            return accountHolder == null
                    ? "%s %s".formatted(bankName, accountNo)
                    : "%s %s (예금주: %s)".formatted(bankName, accountNo, accountHolder);
        }
    }
}
