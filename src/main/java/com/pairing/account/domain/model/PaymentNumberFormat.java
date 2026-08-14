package com.pairing.account.domain.model;

/**
 * 카드번호·계좌번호 입력 형식.
 *
 * <p>가입({@code auth}) 과 마이페이지 결제수단 수정({@code account}) 이 같은 규칙을 써야 해서 한곳에 둔다.
 * 두 곳의 정규식이 갈리면 가입은 통과하는 값이 수정에서 막히거나 그 반대가 된다(실제로 그런 상태였다).
 *
 * <p><b>하이픈·공백은 허용한다.</b> 사용자는 카드 실물에 인쇄된 대로 끊어서 입력하고, 서버가
 * {@code PaymentPolicy} 에서 숫자만 남겨 저장한다. 정규식은 구분자를 허용하되 <b>숫자 개수</b>를 고정한다.
 */
public final class PaymentNumberFormat {

    /** 카드번호: 4자리씩 4묶음, 숫자 16자리. 묶음 사이에만 하이픈·공백을 허용한다. */
    public static final String CARD_NUMBER_REGEX = "^\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}$";

    public static final String CARD_NUMBER_MESSAGE = "카드번호는 숫자 16자리(4자리씩 4묶음)여야 합니다.";

    /**
     * 계좌번호: 숫자 10~14자리. 은행마다 자릿수가 달라 범위로 받는다.
     *
     * <p>숫자 하나로 시작해 뒤에 9~13개가 더 붙는 형태로 쓴다. 각 숫자 앞에만 구분자를 허용하므로
     * 맨 앞·맨 뒤 하이픈이나 "11--22" 처럼 연속된 구분자는 걸러진다.
     */
    public static final String ACCOUNT_NO_REGEX = "^\\d(?:[-\\s]?\\d){9,13}$";

    public static final String ACCOUNT_NO_MESSAGE = "계좌번호는 숫자 10~14자리여야 합니다.";

    private PaymentNumberFormat() {
        throw new IllegalStateException("Utility class");
    }
}
