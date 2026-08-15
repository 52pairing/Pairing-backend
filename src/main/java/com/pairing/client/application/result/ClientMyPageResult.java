package com.pairing.client.application.result;

import com.pairing.account.domain.model.Address;
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
        /** 한 줄로 합친 주소. 화면에 한 줄만 찍는 곳이 쓴다. */
        String address,
        /** 나눠 담긴 주소. 수정 폼이 쓴다. 나눠 담기 전 가입한 계정은 null. */
        Address addressParts,
        ClientGrade grade,
        Double ratingAverage,
        int reviewCount,
        boolean withdrawable
) {
}
