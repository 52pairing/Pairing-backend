package com.pairing.client.application.result;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.domain.model.ClientGrade;

/** 기업정보 + 등급·평점 요약. 전화번호·로고는 계정 공통 화면(06번 도메인) 책임이라 여기 없다. */
public record ClientMyPageResult(
        Long accountId,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount,
        String email,
        String name,
        String address,
        ClientGrade grade,
        Double ratingAverage,
        int reviewCount,
        boolean withdrawable
) {
}
