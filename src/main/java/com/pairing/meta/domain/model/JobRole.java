package com.pairing.meta.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

/** 직무. 요구사항 R21/R30의 26개 항목과 1:1로 대응한다. */
@Getter
@RequiredArgsConstructor
public enum JobRole {

    FRONTEND(JobCategory.DEVELOPMENT, "프론트엔드 개발자"),
    BACKEND(JobCategory.DEVELOPMENT, "백엔드 개발자"),
    FULLSTACK(JobCategory.DEVELOPMENT, "풀스택 개발자"),
    WEB_PUBLISHER(JobCategory.DEVELOPMENT, "웹 퍼블리셔"),
    IOS(JobCategory.DEVELOPMENT, "iOS 개발자"),
    ANDROID(JobCategory.DEVELOPMENT, "Android 개발자"),
    CROSS_PLATFORM(JobCategory.DEVELOPMENT, "크로스플랫폼 개발자"),
    DATA_ENGINEER(JobCategory.DEVELOPMENT, "데이터 엔지니어"),
    DATA_ANALYST(JobCategory.DEVELOPMENT, "데이터 분석가"),
    AI_ML(JobCategory.DEVELOPMENT, "AI·ML 엔지니어"),
    DEVOPS(JobCategory.DEVELOPMENT, "DevOps 엔지니어"),
    CLOUD_INFRA(JobCategory.DEVELOPMENT, "클라우드·인프라 엔지니어"),
    DBA(JobCategory.DEVELOPMENT, "DBA"),
    SECURITY(JobCategory.DEVELOPMENT, "보안 엔지니어"),
    GAME(JobCategory.DEVELOPMENT, "게임 개발자"),
    BLOCKCHAIN(JobCategory.DEVELOPMENT, "블록체인 개발자"),
    EMBEDDED(JobCategory.DEVELOPMENT, "임베디드·하드웨어 개발자"),
    QA(JobCategory.DEVELOPMENT, "QA 엔지니어"),
    UX_UI_DESIGNER(JobCategory.DESIGN, "UX·UI 디자이너"),
    PRODUCT_DESIGNER(JobCategory.DESIGN, "제품 디자이너"),
    WEB_DESIGNER(JobCategory.DESIGN, "웹 디자이너"),
    GRAPHIC_DESIGNER(JobCategory.DESIGN, "그래픽 디자이너"),
    BX_DESIGNER(JobCategory.DESIGN, "BX·브랜드 디자이너"),
    ILLUSTRATOR(JobCategory.DESIGN, "일러스트레이터"),
    MOTION_DESIGNER(JobCategory.DESIGN, "모션·영상 디자이너"),
    THREE_D_DESIGNER(JobCategory.DESIGN, "3D 디자이너");

    private final JobCategory category;
    private final String label;

    public static List<JobRole> ofCategory(JobCategory category) {
        return Arrays.stream(values()).filter(role -> role.category == category).toList();
    }
}
