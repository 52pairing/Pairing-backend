package com.pairing.auth.application.usecase;

import com.pairing.account.domain.model.Role;
import com.pairing.auth.application.command.ClientSignUpCommand;
import com.pairing.auth.application.command.FreelancerSignUpCommand;
import com.pairing.auth.application.command.SocialSignUpCommand;
import com.pairing.auth.application.result.LoginResult;

public interface SignUpUseCase {

    Long signUpClient(ClientSignUpCommand command);

    Long signUpFreelancer(FreelancerSignUpCommand command);

    /** 소셜 가입은 이미 공급자 인증을 마친 상태라 가입 직후 바로 로그인시킨다. */
    LoginResult signUpFreelancerBySocial(SocialSignUpCommand command);

    /** 이메일과 휴대폰은 역할별로 유니크다. 같은 값이라도 다른 역할이면 가입할 수 있다. */
    boolean isEmailDuplicated(String email, Role role);

    boolean isPhoneDuplicated(String phone, Role role);

    boolean isBusinessNoDuplicated(String businessNo);
}
