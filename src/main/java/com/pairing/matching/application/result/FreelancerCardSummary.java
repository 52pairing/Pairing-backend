package com.pairing.matching.application.result;

import com.pairing.freelancer.domain.model.FreelancerGrade;

/** 후보 카드 노출용 프리랜서 요약(이름/사진/등급/평점). freelancer 도메인 소유 데이터. */
public record FreelancerCardSummary(String name, String profileImageUrl, FreelancerGrade grade,
                                    Double ratingAverage, int reviewCount) {
}
