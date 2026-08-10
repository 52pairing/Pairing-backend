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

    /**
     * 직군별 산출물 목록.
     *
     * <p>{@code null} 을 허용한다. 프로젝트가 지워진 계약은 직무를 알 수 없는데(계약은 5년 보관이라
     * 원본보다 오래 남는다) 계약서 열람은 돼야 한다. {@code Map.of} 는 null 키 조회에서
     * NPE 를 던지므로 여기서 먼저 걸러 낸다.
     */
    public static List<String> of(JobCategory jobCategory) {
        return jobCategory == null ? FALLBACK : BY_CATEGORY.getOrDefault(jobCategory, FALLBACK);
    }
}
