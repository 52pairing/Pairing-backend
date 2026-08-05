package com.pairing.auth.application.result;

public record AuthorizeUrlResult(
        String authorizeUrl,
        String state
) {
}
