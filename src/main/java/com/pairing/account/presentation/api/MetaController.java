package com.pairing.account.presentation.api;

import com.pairing.account.domain.model.BankCode;
import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.presentation.api.response.CodeLabelResponse;
import com.pairing.global.common.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 회원가입 화면이 로그인 전에 호출하는 선택 목록.
 *
 * <p>enum 값을 프론트에 하드코딩하면 항목이 늘어날 때 배포가 어긋난다.
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "02. Meta", description = "선택 목록(코드) 조회 API")
public class MetaController {

    @GetMapping("/business-fields")
    @Operation(summary = "사업 분야 목록", description = "클라이언트 회원가입의 사업 분야 선택 목록입니다.")
    public ResponseEntity<ApiResponse<List<CodeLabelResponse>>> getBusinessFields() {
        List<CodeLabelResponse> responseData = Arrays.stream(BusinessField.values())
                .map(field -> new CodeLabelResponse(field.name(), field.getLabel()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("BUSINESS_FIELDS_FOUND", "조회에 성공했습니다.", responseData));
    }

    @GetMapping("/banks")
    @Operation(summary = "은행 목록", description = "계좌 등록에 쓰는 은행 코드 목록입니다. code(금융결제원 기관코드)를 그대로 보냅니다.")
    public ResponseEntity<ApiResponse<List<CodeLabelResponse>>> getBanks() {
        List<CodeLabelResponse> responseData = Arrays.stream(BankCode.values())
                .map(bank -> new CodeLabelResponse(bank.getCode(), bank.getLabel()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("BANKS_FOUND", "조회에 성공했습니다.", responseData));
    }

    @GetMapping("/employee-counts")
    @Operation(summary = "직원수 구간 목록", description = "클라이언트 회원가입의 직원수 선택 목록입니다.")
    public ResponseEntity<ApiResponse<List<CodeLabelResponse>>> getEmployeeCounts() {
        List<CodeLabelResponse> responseData = Arrays.stream(EmployeeCount.values())
                .map(count -> new CodeLabelResponse(count.name(), count.getLabel()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("EMPLOYEE_COUNTS_FOUND", "조회에 성공했습니다.", responseData));
    }
}
