package com.pairing.meta.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.meta.domain.model.JobCategory;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PayUnit;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.SkillLevel;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.meta.presentation.api.response.CodeItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 여러 도메인이 공유하는 선택 목록.
 *
 * <p>프리랜서 조건 등록, 프로젝트 포지션 등록, 검색 필터가 같은 값을 쓴다.
 * 프론트가 enum 을 하드코딩하면 항목이 늘 때 배포가 어긋나므로 여기서 받아 쓴다.
 *
 * <p>비로그인에서도 호출한다. (GlobalSecurityConfig 의 /api/v1/meta/** permitAll)
 */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "02. Meta", description = "선택 목록(코드) 조회 API")
public class CodeController {

    @GetMapping("/job-categories")
    @Operation(summary = "직군 목록")
    public ResponseEntity<ApiResponse<List<CodeItemResponse>>> getJobCategories() {
        List<CodeItemResponse> data = Arrays.stream(JobCategory.values())
                .map(value -> CodeItemResponse.of(value.name(), value.getLabel()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("JOB_CATEGORIES_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/job-roles")
    @Operation(summary = "직무 목록", description = "parentCode 로 직군이 함께 내려갑니다. 직군 선택 시 필터링에 쓰세요.")
    public ResponseEntity<ApiResponse<List<CodeItemResponse>>> getJobRoles() {
        List<CodeItemResponse> data = Arrays.stream(JobRole.values())
                .map(value -> CodeItemResponse.of(value.name(), value.getLabel(), value.getCategory().name()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("JOB_ROLES_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/skills")
    @Operation(summary = "스킬 목록")
    public ResponseEntity<ApiResponse<List<CodeItemResponse>>> getSkills() {
        List<CodeItemResponse> data = Arrays.stream(SkillCode.values())
                .map(value -> CodeItemResponse.of(value.name(), value.getLabel()))
                .toList();

        return ResponseEntity.ok(ApiResponse.success("SKILLS_FOUND", "조회에 성공했습니다.", data));
    }

    @GetMapping("/work-conditions")
    @Operation(summary = "근무 조건 코드 묶음",
            description = "근무 방식·근무 형태·급여 단위·기간 단위·스킬 숙련도를 한 번에 내려줍니다. 화면 하나에서 같이 쓰이므로 묶었습니다.")
    public ResponseEntity<ApiResponse<Map<String, List<CodeItemResponse>>>> getWorkConditions() {
        Map<String, List<CodeItemResponse>> data = Map.of(
                "workStyles", toItems(WorkStyle.values(), WorkStyle::name, WorkStyle::getLabel),
                "workForms", toItems(WorkForm.values(), WorkForm::name, WorkForm::getLabel),
                "payUnits", toItems(PayUnit.values(), PayUnit::name, PayUnit::getLabel),
                "periodUnits", toItems(PeriodUnit.values(), PeriodUnit::name, PeriodUnit::getLabel),
                "skillLevels", toItems(SkillLevel.values(), SkillLevel::name, SkillLevel::getLabel)
        );

        return ResponseEntity.ok(ApiResponse.success("WORK_CONDITIONS_FOUND", "조회에 성공했습니다.", data));
    }

    private <T> List<CodeItemResponse> toItems(T[] values,
                                               java.util.function.Function<T, String> code,
                                               java.util.function.Function<T, String> label) {
        return Arrays.stream(values)
                .map(value -> CodeItemResponse.of(code.apply(value), label.apply(value)))
                .toList();
    }
}
