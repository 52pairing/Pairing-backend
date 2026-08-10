package com.pairing.auth.application.usecase;

import com.pairing.auth.application.command.ConfirmCodeCommand;
import com.pairing.auth.application.command.SendCodeCommand;
import com.pairing.auth.application.result.SendCodeResult;
import com.pairing.auth.domain.model.VerificationPurpose;

public interface EmailVerificationUseCase {

    SendCodeResult send(SendCodeCommand command);

    void confirm(ConfirmCodeCommand command);

    /** 다른 도메인이 민감한 작업(마이페이지 정보 변경 등) 전에 이메일 인증을 마쳤는지 확인할 때 쓴다. */
    boolean isVerified(String email, VerificationPurpose purpose);

    /** 인증 마커는 1회용이다. 민감한 작업을 마친 뒤 호출해서 같은 인증으로 재사용되지 않게 한다. */
    void clearVerification(String email, VerificationPurpose purpose);
}
