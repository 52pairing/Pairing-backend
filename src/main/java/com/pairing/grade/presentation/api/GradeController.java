package com.pairing.grade.presentation.api;

import com.pairing.account.domain.model.Role;
import com.pairing.client.domain.model.ClientGrade;
import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.global.annotation.swagger.ApiErrorCodeExample;
import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.global.security.CurrentAccountId;
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

import java.math.BigDecimal;
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
 * <p>스켈레톤이라 고정 응답을 돌려준다.
 */
@RestController
@RequestMapping("/api/v1/grades")
@RequiredArgsConstructor
@Validated
@Tag(name = "07. Grade", description = "등급/혜택 API")
public class GradeController {

    @GetMapping
    @Operation(summary = "등급 기준표",
            description = "역할별 등급과 승급 조건·유지 기준·혜택·수수료율입니다. 로그인 없이 조회할 수 있습니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"INVALID_REQUEST"})
    public ResponseEntity<ApiResponse<List<GradeResponse>>> findAll(
            @RequestParam @NotNull(message = "역할은 필수입니다.") Role role
    ) {
        // TODO: 등급 정책 조회 (정책이 코드 상수라면 그대로 매핑)
        List<GradeResponse> grades = role == Role.FREELANCER ? sampleFreelancerGrades() : sampleClientGrades();

        return ResponseEntity.ok(ApiResponse.success("GRADES_FOUND", "조회에 성공했습니다.", grades));
    }

    @GetMapping("/me")
    @Operation(summary = "내 등급 현황",
            description = "현재 등급과 다음 등급까지 남은 조건입니다. 등급은 매월 자동 재산정됩니다.")
    @ApiErrorCodeExample(domain = GlobalErrorCode.class, value = {"UNAUTHORIZED"})
    public ResponseEntity<ApiResponse<MyGradeResponse>> findMine(@CurrentAccountId Long accountId) {
        // TODO: 역할 판별 -> 완료 건수/평점 집계 -> 다음 등급 조건 계산
        return ResponseEntity.ok(ApiResponse.success("MY_GRADE_FOUND", "조회에 성공했습니다.",
                new MyGradeResponse(ClientGrade.SILVER.name(), ClientGrade.SILVER.getLabel(), 4, 4.2,
                        ClientGrade.GOLD.name(), "완료 건수 6건이 더 필요합니다.", "매월 1일 자동 산정")));
    }

    // ==========================================
    // 스켈레톤 고정 응답. 구현하면서 제거한다.
    // ==========================================

    private static final GradeResponse.FeeRate BASE_RATE = new GradeResponse.FeeRate(
            new BigDecimal("3.00"), new BigDecimal("2.00"),
            new BigDecimal("7.00"), new BigDecimal("6.00"));

    private static final GradeResponse.FeeRate TOP_RATE = new GradeResponse.FeeRate(
            new BigDecimal("2.00"), new BigDecimal("1.00"),
            new BigDecimal("6.00"), new BigDecimal("5.00"));

    private static final String CLIENT_MAINTENANCE = "12개월 내 프로젝트 경험 · 매월 체크";
    private static final String FREELANCER_MAINTENANCE = "6개월 내 프로젝트 경험 유지";

    private List<GradeResponse> sampleClientGrades() {
        return List.of(
                new GradeResponse(Role.CLIENT, ClientGrade.SILVER.name(), ClientGrade.SILVER.getLabel(), 1,
                        "기본 등급", CLIENT_MAINTENANCE,
                        List.of(new GradeResponse.Benefit("매칭 프리랜서 수", "1명"),
                                new GradeResponse.Benefit("프로젝트 등록", "최대 1개"),
                                new GradeResponse.Benefit("우수 프리랜서 매칭", "매칭 확률 증가")),
                        BASE_RATE, "기본 수수료"),
                new GradeResponse(Role.CLIENT, ClientGrade.GOLD.name(), ClientGrade.GOLD.getLabel(), 2,
                        "별점 평균 3점 이상 + 완료 건수 10건 이상", CLIENT_MAINTENANCE,
                        List.of(new GradeResponse.Benefit("매칭 프리랜서 수", "1명"),
                                new GradeResponse.Benefit("프로젝트 등록", "최대 2개"),
                                new GradeResponse.Benefit("높은 등급 프리랜서 매칭", "매칭 확률 증가")),
                        BASE_RATE, "기본 수수료"),
                new GradeResponse(Role.CLIENT, ClientGrade.DIAMOND.name(), ClientGrade.DIAMOND.getLabel(), 3,
                        "별점 평균 4점 이상 + 완료 건수 20건 이상", CLIENT_MAINTENANCE,
                        List.of(new GradeResponse.Benefit("매칭 프리랜서 수", "1명"),
                                new GradeResponse.Benefit("프로젝트 등록", "최대 2개"),
                                new GradeResponse.Benefit("높은 등급 프리랜서 매칭", "매칭 확률 증가"),
                                new GradeResponse.Benefit("착수금 수수료", "1% 인하"),
                                new GradeResponse.Benefit("성공보수 수수료", "1% 인하 (총 2% 인하)")),
                        TOP_RATE, "수수료 각 1% 인하 (총 2% 인하)"));
    }

    private List<GradeResponse> sampleFreelancerGrades() {
        List<GradeResponse.Benefit> baseBenefits = List.of(
                new GradeResponse.Benefit("표준계약서 작성", "제공"),
                new GradeResponse.Benefit("검증된 프로젝트 매칭", "제공"),
                new GradeResponse.Benefit("AI 1:1 맞춤 매칭", "제공"));

        List<GradeResponse.Benefit> seniorBenefits = List.of(
                new GradeResponse.Benefit("표준계약서 작성", "제공"),
                new GradeResponse.Benefit("검증된 프로젝트 매칭", "제공"),
                new GradeResponse.Benefit("AI 1:1 맞춤 매칭", "제공"),
                new GradeResponse.Benefit("우수 클라이언트 매칭", "매칭 확률 증가"));

        return List.of(
                new GradeResponse(Role.FREELANCER, FreelancerGrade.JUNIOR.name(),
                        FreelancerGrade.JUNIOR.getLabel(), 1, "디폴트 (가입 즉시)", FREELANCER_MAINTENANCE,
                        baseBenefits, BASE_RATE, "기본 수수료"),
                new GradeResponse(Role.FREELANCER, FreelancerGrade.SENIOR.name(),
                        FreelancerGrade.SENIOR.getLabel(), 2, "별점 3점 이상 + 완료 5건 이상",
                        FREELANCER_MAINTENANCE, seniorBenefits, BASE_RATE, "기본 수수료"),
                new GradeResponse(Role.FREELANCER, FreelancerGrade.MASTER.name(),
                        FreelancerGrade.MASTER.getLabel(), 3, "별점 4점 이상 + 완료 10건 이상",
                        FREELANCER_MAINTENANCE, seniorBenefits, TOP_RATE, "수수료 각 1% 인하 (총 2% 인하)"));
    }
}
