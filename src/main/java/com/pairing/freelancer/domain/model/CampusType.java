package com.pairing.freelancer.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 본교/분교 구분. */
@Getter
@RequiredArgsConstructor
public enum CampusType {

    MAIN("본교"),
    BRANCH("분교");

    private final String label;
}
