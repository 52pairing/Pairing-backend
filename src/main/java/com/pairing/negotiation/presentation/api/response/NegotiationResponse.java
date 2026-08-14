package com.pairing.negotiation.presentation.api.response;

import com.pairing.negotiation.domain.model.ConditionStatus;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.FloorComparison;
import com.pairing.negotiation.domain.model.NegotiationAgentState;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 협상 상세. 협상 화면 상단과 조건 카드에 필요한 값이다. */
@Schema(description = "협상 상세 응답")
public record NegotiationResponse(

        @Schema(description = "협상 ID", example = "300")
        Long negotiationId,

        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,

        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼")
        String projectTitle,

        @Schema(description = "포지션 ID", example = "10")
        Long positionId,

        @Schema(description = "상대 이름", example = "홍길동")
        String counterpartName,

        @Schema(description = "내 역할. 말풍선 좌/우 배치와 '내 대리인' 표기에 쓴다", example = "FREELANCER")
        PartyRole viewerRole,

        @Schema(description = "내 응답 차례 여부. true 면 승인/재지시 패널을 띄운다. "
                + "조건 status 만으로는 '상대 응답 대기'와 구분되지 않는다", example = "true")
        boolean waitingForMe,

        @Schema(description = "대리인(AI) 실행 상태. 대기 문구는 이 값을 먼저 보고 정한다. "
                + "RUNNING=\"AI 대리인이 협상 중\"(진행 표시) · "
                + "FAILED=\"대리인 호출 실패\"(재시도 안내) · "
                + "IDLE=대리인은 안 도는 중이니 waitingForMe 로 판정한다"
                + "(true=내 차례, false 이고 totalRound>0 이면 상대 응답 대기, "
                + "false 이고 totalRound=0 이면 상대가 아직 마지노선을 안 냈다). "
                + "RUNNING 동안에는 상세를 폴링하거나 STOMP 이벤트를 기다린다", example = "RUNNING")
        NegotiationAgentState agentState,

        @Schema(description = "상태")
        NegotiationStatus status,

        @Schema(description = "진행 라운드 수", example = "3")
        int totalRound,

        @Schema(description = "라운드 상한. 소진 시 자동 결렬된다.", example = "15")
        int maxRound,

        @Schema(description = "합의 금액(원). 타결 전에는 null", example = "22000000")
        Long agreedAmount,

        @Schema(description = "협상 채팅방 ID. AI Out 이후 사람 채팅으로 쓴다.", example = "500")
        Long chatRoomId,

        @Schema(description = "AI 협상 종료(AI Out) 시각")
        LocalDateTime aiOutAt,

        @Schema(description = "협상이 끝난 시각. 진행 중이면 null. 타결·결렬 모두 채워진다")
        LocalDateTime endedAt,

        @Schema(description = "종료 사유. **결렬일 때만** 값이 있다(타결은 사유가 없으므로 null). "
                + "사람이 포기하면 입력한 사유가, 사유를 안 적었으면 \"협상 포기\"가, "
                + "라운드 상한을 다 쓰면 \"라운드 상한(15회) 소진으로 자동 결렬\"이 온다. "
                + "**사람이 쓴 문장이 그대로 오므로 화면에 그대로 노출하기 전에 이스케이프할 것**",
                example = "근무 형태 조건 차이가 좁혀지지 않아 협상을 종료합니다.")
        String endReason,

        @Schema(description = "[미사용] 15회 자동 결렬 채택으로 폐기. 항상 false", example = "false")
        boolean finalApprovalRequired,

        @Schema(description = "**최종 절충 단계 여부.** 라운드 상한(15회)까지 합의에 이르지 못하면 true 가 된다. "
                + "true 면 화면을 '최종 절충안' 상태로 전환하고, "
                + "라운드 배지 대신 별도 상태를 노출하며, 조건 카드의 compromiseValue(절충값)를 보여준다. "
                + "이 단계에서는 조건별 응답(/answers)이 아니라 절충안 전체 수락(/final-offer/accept) 또는 "
                + "포기(/give-up)만 가능하다. status 는 여전히 IN_PROGRESS 다.", example = "true")
        boolean finalOffer,

        @Schema(description = "최종 절충안을 **내가** 수락했는가. 양측이 모두 수락해야 타결된다. "
                + "true 면 화면은 '상대 수락 대기' 상태로 둔다(내 수락 버튼 비활성).", example = "false")
        boolean myFinalAccepted,

        @Schema(description = "최종 절충안을 **상대가** 수락했는가. true 이고 내가 아직이면 "
                + "'상대는 수락했습니다 — 수락하면 타결됩니다' 안내에 쓴다.", example = "false")
        boolean counterpartFinalAccepted,

        @Schema(description = "협상 조건 목록")
        List<Condition> conditions
) {

    @Schema(description = "협상 조건")
    public record Condition(
            @Schema(description = "조건 ID", example = "401") Long conditionId,
            @Schema(description = "조건 종류") ConditionType type,
            @Schema(description = "클라이언트 희망값(공개·고정). 초기 제안 카드·상대 희망 힌트용. 마지노선 아님", example = "20000000") String clientValue,
            @Schema(description = "프리랜서 희망값(공개·고정). 마지노선 아님", example = "25000000") String freelancerValue,
            @Schema(description = "AI 제안값", example = "22000000") String proposedValue,
            @Schema(description = "제안 근거", example = "프리랜서 경력이 요구 수준을 넘어 중간값을 제안합니다.") String reason,
            @Schema(description = "합의값. 합의 전에는 null") String agreedValue,
            @Schema(description = "조건 상태") ConditionStatus status,
            @Schema(description = "이 조건의 라운드 수", example = "2") int roundCount,
            @Schema(description = "내 마지노선(직전 입력값). 뷰어 본인 것만 내려감. 상대 마지노선은 절대 노출하지 않는다. 재지시 '직전 마지노선' 표시에 쓴다.", example = "3500000") String myFloor,
            @Schema(description = "마지노선 비교 방식. RANGE=크기 비교(\"480만 원 이상이어야 합니다\"), "
                    + "CHOICE=허용값 집합(\"재택을 허용해야 합니다\"), NONE=비교 기준 없음(안내 불필요). "
                    + "RANGE 의 이상/이하 방향은 viewerRole 로 판단한다 — 클라는 상한, 프리는 하한이다.",
                    example = "CHOICE") FloorComparison floorComparison,
            @Schema(description = "**최종 절충값.** finalOffer=true 인 미합의 조건에만 채워진다(양쪽이 마지노선을 넘겨 "
                    + "만나는 중간 지점: 숫자·기간·날짜는 중간값, 근무 방식/형태는 ANY=모두 가능). 이미 합의된 조건이나 "
                    + "절충 불가 조건은 null. 화면은 이 값을 '최종 절충안'으로 보여주고, 양측이 수락하면 이 값이 "
                    + "agreedValue 로 확정된다.", example = "4000000") String compromiseValue
    ) {
    }
}
