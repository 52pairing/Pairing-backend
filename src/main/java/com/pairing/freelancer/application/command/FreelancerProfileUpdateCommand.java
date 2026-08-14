package com.pairing.freelancer.application.command;

import com.pairing.account.domain.model.Address;

public record FreelancerProfileUpdateCommand(
        Long accountId,
        Long profileFileId,
        String phone,
        Address address,
        boolean aiMatchingAgreed
) {
}
