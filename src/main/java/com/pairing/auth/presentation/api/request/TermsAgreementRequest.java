package com.pairing.auth.presentation.api.request;

import com.pairing.terms.application.command.AgreeTermsCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "약관 동의 항목")
public record TermsAgreementRequest(

        @Schema(description = "약관 ID", example = "1")
        @NotNull(message = "약관 ID는 필수입니다.")
        Long termsId,

        @Schema(description = "동의 여부", example = "true")
        boolean agreed
) {

    public AgreeTermsCommand toCommand() {
        return new AgreeTermsCommand(termsId, agreed);
    }
}
