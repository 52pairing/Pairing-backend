package com.pairing.auth.infrastructure.mail;

import com.pairing.auth.application.port.MailSenderPort;
import com.pairing.auth.exception.AuthErrorCode;
import com.pairing.auth.settings.AuthSettings;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 메일 발송.
 *
 * <p>발송 실패를 삼키면 사용자는 오지 않는 코드를 계속 기다린다. 예외로 올려서 재시도를 유도한다.
 * 본문에 코드/임시 비밀번호가 들어가므로 로그에 body를 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpMailSenderAdapter implements MailSenderPort {

    private final JavaMailSender javaMailSender;
    private final AuthSettings authSettings;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authSettings.getFromAddress());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패: subject={}, cause={}", subject, e.getMessage());
            throw new BusinessException(AuthErrorCode.MAIL_SEND_FAILED);
        }
    }
}
