package com.pairing.global.port.out;

/**
 * 개인정보/금융정보를 DB에 저장하기 전에 암호화한다.
 *
 * <p>카드번호와 계좌번호는 평문 저장이 금지되어 있어(스키마 주석) BYTEA 컬럼에 암호문만 넣는다.
 * 애플리케이션 계층은 알고리즘을 모르고 이 포트에만 의존한다.
 */
public interface DataEncryptionPort {

    /** 평문을 암호화한다. null이면 null을 반환한다. */
    byte[] encrypt(String plainText);

    /** 암호문을 복호화한다. null이면 null을 반환한다. */
    String decrypt(byte[] cipherText);
}
