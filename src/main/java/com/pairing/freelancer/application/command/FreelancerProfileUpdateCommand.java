package com.pairing.freelancer.application.command;

public record FreelancerProfileUpdateCommand(
        Long accountId,
        String currentPassword,
        Long profileFileId,
        String phone,
        String address,
        boolean aiMatchingAgreed
) {
}
