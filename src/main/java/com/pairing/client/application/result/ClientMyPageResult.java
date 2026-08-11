package com.pairing.client.application.result;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.domain.model.ClientGrade;

/** 기업정보 + 등급·평점 요약. 마이페이지 기본 정보 탭과 기업 정보 탭이 같은 값을 나눠 쓴다. */
public record ClientMyPageResult(
        Long accountId,
        String logoUrl,
        String companyName,
        String businessNo,
        BusinessField businessField,
        EmployeeCount employeeCount,
        String email,
        String name,
        String phone,
        String address,
        ClientGrade grade,
        Double ratingAverage,
        int reviewCount,
        boolean withdrawable
) {
}
