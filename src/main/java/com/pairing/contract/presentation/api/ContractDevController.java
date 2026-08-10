package com.pairing.contract.presentation.api;

import com.pairing.contract.application.usecase.ContractCreationUseCase;
import com.pairing.global.common.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계약 도메인 단독 검증용 임시 진입점.
 *
 * <p><b>협상 쪽 준비가 끝나면 이 클래스를 통째로 지운다.</b> 계약서 생성은 협상 타결 시 서버가
 * 수행하므로 원래 엔드포인트가 없다. 협상 흐름을 태우지 않고 계약만 확인하려고 잠시 열어 둔다.
 *
 * <p>부르는 것은 협상이 부르는 것과 같은 유스케이스라, 계약 도메인 안에서 일어나는 일은 동일하다.
 * 차이는 협상 타결 트랜잭션에 참여하지 않는다는 것뿐이고 그건 협상 쪽 롤백 동작이다.
 *
 * <p>운영에서는 뜨지 않는다. 지우는 걸 잊어도 사고로 이어지지 않게 프로파일로 막아 둔다.
 *
 * <p>경로를 {@code /admin} 이 아니라 {@code /dev} 로 둔 이유는, 보안 설정이
 * {@code /api/v1/*&#47;admin/**} 에 {@code ROLE_ADMIN} 을 요구하기 때문이다. 검증하려고 관리자
 * 계정까지 만들 필요는 없다. 로그인은 필요하다({@code anyRequest().authenticated()}).
 */
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/contracts/dev")
@RequiredArgsConstructor
@Tag(name = "14. Contract", description = "계약 API")
public class ContractDevController {

    private final ContractCreationUseCase contractCreationUseCase;

    @PostMapping("/from-negotiation/{negotiationId}")
    @Operation(summary = "[임시] 타결된 협상으로 계약서 생성",
            description = "협상 도메인이 타결 시 부르는 것과 같은 경로입니다. 계약 도메인만 검증하려고 "
                    + "열어 둔 진입점이며, 협상 연동이 끝나면 제거합니다. "
                    + "협상이 AGREED 상태가 아니면 NG_009, 이미 만들어져 있으면 기존 계약 ID 를 돌려줍니다.")
    public ResponseEntity<ApiResponse<Long>> createFromNegotiation(@PathVariable Long negotiationId) {
        return ResponseEntity.ok(ApiResponse.success("CONTRACT_CREATED", "계약서를 생성했습니다.",
                contractCreationUseCase.createFromNegotiation(negotiationId)));
    }
}
