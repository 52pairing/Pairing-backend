package com.pairing.support.application.command;

public record AskChatbotCommand(
        Long accountId,
        Long sessionId,
        String question
) {
}
