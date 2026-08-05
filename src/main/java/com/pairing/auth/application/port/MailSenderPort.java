package com.pairing.auth.application.port;

/** 인증 메일 발송. */
public interface MailSenderPort {

    void send(String to, String subject, String body);
}
