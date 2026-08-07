-- 협상 마지노선(floor) 분리 저장 — #1 결정 반영
-- negotiation_condition.client_value/freelancer_value = 각 측 희망값(공개·고정)
-- 마지노선(비공개)은 아래 컬럼에 별도 저장. 응답엔 뷰어 본인 것만 myFloor 로 내림.

ALTER TABLE "negotiation_condition"
    ADD COLUMN "client_floor"     VARCHAR(255),   -- 클라이언트 마지노선(비공개). 숫자형=상한, 범주형=허용옵션 CSV
    ADD COLUMN "freelancer_floor" VARCHAR(255);   -- 프리랜서 마지노선(비공개). 숫자형=하한, 범주형=허용옵션 CSV

-- 참고:
-- 1) client_value / freelancer_value 는 협상 시작 시점 diff 로 계산된 희망값이며 협상 중 불변.
-- 2) client_floor / freelancer_floor 는 /start·재지시(/answers)에서 각 측이 입력·갱신. round_count 와 함께 변함.
-- 3) 응답 DTO(NegotiationResponse.Condition)는 상대 floor 를 절대 포함하지 않는다. 뷰어 role 에 맞는 것만 myFloor 로.
-- 4) negotiation.floor_amount(단건) 는 AMOUNT 조건의 요청자 floor 와 중복 → 신규 개발부터는 condition 별 floor 를 정본으로,
--    floor_amount 는 하위호환/조회 편의용으로만 유지하거나 제거 검토.
