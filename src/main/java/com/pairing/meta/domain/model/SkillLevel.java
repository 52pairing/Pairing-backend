package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 스킬 숙련도. */
@Getter
@RequiredArgsConstructor
public enum SkillLevel {

    BEGINNER("초급"),
    INTERMEDIATE("중급"),
    ADVANCED("고급");

    private final String label;
}
