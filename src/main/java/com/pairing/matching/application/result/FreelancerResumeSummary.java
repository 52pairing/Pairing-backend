package com.pairing.matching.application.result;

import java.util.List;

/** 프리랜서 임베딩용 이력서 요약(자기소개+경력사항). freelancer 도메인 소유 데이터. */
public record FreelancerResumeSummary(String selfIntroduction, List<CareerEntry> careers) {

    public record CareerEntry(String companyName, String departmentRank, String jobDescription) {
    }
}
