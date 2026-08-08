package com.pairing.grade.presentation.api;

import com.pairing.account.domain.model.Role;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.grade.application.usecase.GradeQueryUseCase;
import com.pairing.grade.exception.GradeErrorCode;
import com.pairing.grade.presentation.api.response.GradeResponse;
import com.pairing.grade.presentation.api.response.MyGradeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 등급과 혜택. (등급 안내 화면 / 마이페이지 &gt; 등급 및 혜택 / 로그인 메인의 등급별 혜택 표)
 *
 * <p>등급 기준표는 로그인 없이도 볼 수 있어야 해서 공개다. 내 등급 조회만 인증이 필요하다.
 * 등급은 시스템이 자동 산정하므로 변경 API 는 없다.
 *
 * <p>역할에 따라 등급 코드가 다르다. 클라이언트는 SILVER/GOLD/DIAMOND, 프리랜서는 JUNIOR/SENIOR/MASTER.
 * 클라이언트 화면은 수수료를 숫자 표로, 프리랜서 화면은 문구로 보여줘서 두 형태를 모두 내려준다.
 *
 * <p>{@code GET /me} 의 완료 건수는 contract 도메인이 아직 없어 항상 0이고, 다음 등급 안내 문구도
 * 별점 조건 충족 여부만 알려준다(TODO: contract 도메인 구현되면 완료 건수까지 정확히 계산).
 */
@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
@Validated
@Tag(name = "07. Grade", description = "등급/혜택 API")
public class GradeController {

    private final GradeQueryUseCase gradeQueryUseCase;

    @GetMapping
    @Operation(summary = "등급 기준표",
            description = "역할별 등급과 승급 조건·유지 기준·혜택·수수료율입니다. 로그인 없이 조회할 수 있습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    @ApiErrorCodeExample(domain = GradeErrorCode.class, value = {"INVALID_ROLE"})
    public ResponseEntity<ApiResponse<List<GradeResponse>>> findAll(
            @RequestParam @NotNull(message = "역할은 필수입니다.") Role role
    ) {
        List<GradeResponse> grades = gradeQueryUseCase.findByRole(role).stream()
                .map(GradeResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.success("GRADES_FOUND", "조회에 성공했습니다.", grades));
    }

    @GetMapping("/me")
    @Operation(summary = "내 등급 현황",
            description = "현재 등급과 다음 등급까지 남은 조건입니다. 등급은 매월 자동 재산정됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<MyGradeResponse>> findMine(@CurrentAccountId Long accountId) {
        MyGradeResponse response = MyGradeResponse.from(gradeQueryUseCase.getMyGrade(accountId));
        return ResponseEntity.ok(ApiResponse.success("MY_GRADE_FOUND", "조회에 성공했습니다.", response));
    }
}
