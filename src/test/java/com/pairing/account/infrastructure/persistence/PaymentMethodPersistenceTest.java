package com.pairing.account.infrastructure.persistence;

import com.pairing.account.domain.model.Account;
import com.pairing.account.domain.model.PaymentMethod;
import com.pairing.account.domain.model.PaymentMethodType;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.repository.AccountRepository;
import com.pairing.account.domain.repository.PaymentMethodRepository;
import com.pairing.global.port.out.DataEncryptionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제수단 매핑과 암호화 왕복 확인.
 *
 * <p>회원가입에서는 결제수단을 받지 않는다(마이페이지 소관). 그래서 이 매핑을 지나가는 흐름이 없어
 * 여기서 직접 확인한다. BYTEA(byte[])와 CHAR(4) 매핑이 깨지면 마이페이지 작업 때 알게 되는 것보다 낫다.
 */
@SpringBootTest
@Transactional
class PaymentMethodPersistenceTest {

    private static final String CARD_NUMBER = "1234567812345678";
    private static final String ACCOUNT_NO = "11012345678901";

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private PaymentMethodRepository paymentMethodRepository;
    @Autowired
    private DataEncryptionPort dataEncryptionPort;

    private Long createAccount() {
        return accountRepository.save(Account.createByEmail(
                "payment-test@pairing.com", "$2a$10$hash", Role.FREELANCER, "홍길동", "01099998888")).getId();
    }

    @Test
    @DisplayName("카드와 계좌를 저장하고 복호화해서 원래 값을 되찾는다")
    void savesAndDecrypts() {
        Long accountId = createAccount();

        paymentMethodRepository.saveAll(List.of(
                PaymentMethod.createCard(accountId, dataEncryptionPort.encrypt(CARD_NUMBER), "신한카드", "5678"),
                PaymentMethod.createBankAccount(
                        accountId, "088", dataEncryptionPort.encrypt(ACCOUNT_NO), "6789", "홍길동")));

        List<PaymentMethod> saved = paymentMethodRepository.findAllByAccountId(accountId);
        assertThat(saved).hasSize(2);

        PaymentMethod card = saved.stream()
                .filter(method -> method.getMethodType() == PaymentMethodType.CARD)
                .findFirst().orElseThrow();
        PaymentMethod bank = saved.stream()
                .filter(method -> method.getMethodType() == PaymentMethodType.BANK_ACCOUNT)
                .findFirst().orElseThrow();

        // 평문은 어디에도 저장되지 않고, 화면 표시용 끝 4자리만 남는다.
        assertThat(card.getCardLast4()).isEqualTo("5678");
        assertThat(new String(card.getCardNumberEnc())).doesNotContain(CARD_NUMBER);
        assertThat(dataEncryptionPort.decrypt(card.getCardNumberEnc())).isEqualTo(CARD_NUMBER);

        assertThat(bank.getBankCode()).isEqualTo("088");
        assertThat(dataEncryptionPort.decrypt(bank.getAccountNoEnc())).isEqualTo(ACCOUNT_NO);
    }

    @Test
    @DisplayName("같은 값을 암호화해도 매번 다른 암호문이 나온다")
    void encryptionUsesRandomIv() {
        byte[] first = dataEncryptionPort.encrypt(CARD_NUMBER);
        byte[] second = dataEncryptionPort.encrypt(CARD_NUMBER);

        // IV가 고정이면 같은 카드번호가 항상 같은 바이트로 저장돼 대조만으로 값을 알아낼 수 있다.
        assertThat(first).isNotEqualTo(second);
        assertThat(dataEncryptionPort.decrypt(first)).isEqualTo(dataEncryptionPort.decrypt(second));
    }
}
