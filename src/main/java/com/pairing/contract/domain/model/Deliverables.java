package com.pairing.contract.domain.model;

import com.pairing.meta.domain.model.JobCategory;

import java.util.List;
import java.util.Map;

/**
 * 계약서 제2조 산출물. 직군별 표준 문구를 쓴다.
 *
 * <p>계약서 항목이지만 프로젝트 등록에도 협상에도 입력란이 없다. 클라이언트가 정하는 값이
 * 아니라 직군이 정해지면 따라오는 관례적 목록이라 상수로 둔다.
 *
 * <p>직군이 개발·디자인 둘뿐이라 {@code JobRole} 27개까지 세분화하지 않았다.
 * 필요해지면 그때 나누면 된다.
 */
public final class Deliverables {

    private static final Map<JobCategory, List<String>> BY_CATEGORY = Map.of(
            JobCategory.DEVELOPMENT, List.of("소스코드", "API 명세서", "배포 가이드"),
            JobCategory.DESIGN, List.of("디자인 시안", "소스 파일", "스타일 가이드"));

    /** 직군이 늘었는데 여기 안 넣은 경우. 계약서가 비는 것보다는 낫다. */
    private static final List<String> FALLBACK = List.of("산출물 일체");

    private Deliverables() {
        throw new IllegalStateException("Utility class");
    }

    public static List<String> of(JobCategory jobCategory) {
        return BY_CATEGORY.getOrDefault(jobCategory, FALLBACK);
    }
}
