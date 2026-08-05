package com.pairing.auth.application.command;

public record ChangePasswordCommand(
        Long accountId,
        String currentPassword,
        String newPassword,
        String newPasswordConfirm
) {
}
