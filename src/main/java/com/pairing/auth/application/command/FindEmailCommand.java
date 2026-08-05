package com.pairing.auth.application.command;

public record FindEmailCommand(
        String name,
        String phone
) {
}
