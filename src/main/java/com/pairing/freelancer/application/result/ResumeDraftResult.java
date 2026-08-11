package com.pairing.freelancer.application.result;

import java.time.LocalDateTime;

/** 임시 저장된 초안. {@code payload} 는 화면이 저장했던 JSON 그대로다. */
public record ResumeDraftResult(
        String payload,
        LocalDateTime savedAt
) {
}
