package com.pairing.auth.application.result;

import java.time.LocalDateTime;

public record SendCodeResult(
        LocalDateTime expiresAt,
        int remainingSendCount
) {
}
