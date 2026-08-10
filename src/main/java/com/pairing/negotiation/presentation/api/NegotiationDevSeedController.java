package com.pairing.negotiation.presentation.api;

import com.pairing.global.common.api.response.ApiResponse;
import com.pairing.global.security.CurrentAccountId;
import com.pairing.negotiation.application.service.NegotiationDevSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 프론트 연동용 협상 더미 생성 API. <b>개발 편의 기능이며 실서비스 오픈 전에 삭제한다.</b>
 *
 * <p>{@code app.dev.seed-enabled=true} 일 때만 등록된다(기본 false). 로그인한 본인이 소유한
 * 프로젝트에만 만들 수 있다.
 */
@RestController
@RequestMapping("/api/v1/dev/negotiations")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev.seed-enabled", havingValue = "true")
@Tag(name = "99. Dev (임시)", description = "프론트 연동용 더미 데이터. 오픈 전 삭제 예정")
public class NegotiationDevSeedController {

    private final NegotiationDevSeedService devSeedService;

    @PostMapping("/seed")
    @Operation(summary = "협상 더미 생성 (개발용)",
            description = "내가 소유한 프로젝트에 화면 상태별 협상 5건을 만듭니다. "
                    + "BEFORE_START(마지노선 입력 전) / WAITING_FOR_ME(내 응답 차례) / "
                    + "NEEDS_REDIRECT(재지시 필요) / AGREED(타결) / FAILED(결렬). "
                    + "여러 번 호출하면 그만큼 쌓입니다.")
    public ResponseEntity<ApiResponse<List<NegotiationDevSeedService.SeededNegotiation>>> seed(
            @RequestParam Long projectId,
            @CurrentAccountId Long accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success("NEGOTIATION_SEEDED", "더미 협상을 생성했습니다.",
                devSeedService.seed(projectId, accountId)));
    }
}
