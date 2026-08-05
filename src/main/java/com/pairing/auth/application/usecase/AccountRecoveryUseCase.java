package com.pairing.auth.application.usecase;

import com.pairing.auth.application.command.ChangePasswordCommand;
import com.pairing.auth.application.command.FindEmailCommand;
import com.pairing.auth.application.command.PasswordResetRequestCommand;
import com.pairing.auth.application.command.UnlockCommand;
import com.pairing.auth.application.result.MaskedEmailResult;

import java.util.List;

public interface AccountRecoveryUseCase {

    /** 아이디 찾기. 마스킹된 이메일을 역할과 함께 반환한다. */
    List<MaskedEmailResult> findMaskedEmails(FindEmailCommand command);

    /** 재설정 링크 발송. 계정 열거를 막기 위해 일치하지 않아도 정상 응답한다. */
    void requestPasswordReset(PasswordResetRequestCommand command);

    /** 링크의 토큰을 확인하고 임시 비밀번호를 발급한다. */
    void issueTempPassword(String resetToken);

    void changePassword(ChangePasswordCommand command);

    void unlock(UnlockCommand command);
}
