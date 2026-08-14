/* 자동 생성 파일 — 직접 고치지 않는다.
 *
 * 원본은 k6/endpoints.js 다. 다시 만들려면:
 *   k6 run k6/tools/dump-catalog.js
 *
 * 빌더 GUI 가 file:// 로 열려야 해서 일반 스크립트(전역 변수)로 내보낸다.
 * ES 모듈은 file:// 에서 import 가 막힌다.
 */
window.K6_CATALOG = [
  {
    "key": "home_summary",
    "method": "GET",
    "path": "/api/v1/home/summary",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "메인 지표",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "home_reviews",
    "method": "GET",
    "path": "/api/v1/home/site-reviews",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "메인 노출 후기",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "home_faqs",
    "method": "GET",
    "path": "/api/v1/home/faqs",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "메인 FAQ",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_fields",
    "method": "GET",
    "path": "/api/v1/meta/business-fields",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "업종 목록",
    "warn": "불변 마스터 데이터. 캐시 개선 효과를 보여주기 좋다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_banks",
    "method": "GET",
    "path": "/api/v1/meta/banks",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "은행 목록",
    "warn": "불변 마스터 데이터",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_employees",
    "method": "GET",
    "path": "/api/v1/meta/employee-counts",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "사원수 구간",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_jobcats",
    "method": "GET",
    "path": "/api/v1/meta/job-categories",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "직군 목록",
    "warn": "불변 마스터 데이터",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_jobroles",
    "method": "GET",
    "path": "/api/v1/meta/job-roles",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "직무 목록",
    "warn": "불변 마스터 데이터",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_skills",
    "method": "GET",
    "path": "/api/v1/meta/skills",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "스킬 목록",
    "warn": "목록이 커서 직렬화 비용이 드러난다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "meta_workcond",
    "method": "GET",
    "path": "/api/v1/meta/work-conditions",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "근무 조건 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "terms_list",
    "method": "GET",
    "path": "/api/v1/terms",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "약관 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "terms_docs",
    "method": "GET",
    "path": "/api/v1/terms/documents",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "약관 본문",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "grades_table",
    "method": "GET",
    "path": "/api/v1/grades",
    "target": "spring",
    "auth": "none",
    "group": "public",
    "label": "등급 기준표",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "me",
    "method": "GET",
    "path": "/api/v1/auth/me",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 정보",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "grade_me",
    "method": "GET",
    "path": "/api/v1/grades/me",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 등급",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "notifs",
    "method": "GET",
    "path": "/api/v1/notifications",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "알림 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "notifs_unread",
    "method": "GET",
    "path": "/api/v1/notifications/unread-count",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "안 읽은 알림 수",
    "warn": "프론트가 폴링하는 경로. 실제 트래픽 비중이 가장 높다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "chatrooms",
    "method": "GET",
    "path": "/api/v1/chat-rooms",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "채팅방 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "chat_unread",
    "method": "GET",
    "path": "/api/v1/chat-rooms/unread-count",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "안 읽은 메시지 수",
    "warn": "프론트가 폴링하는 경로",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "reviews_recv",
    "method": "GET",
    "path": "/api/v1/reviews/received",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "받은 평가",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "reviews_written",
    "method": "GET",
    "path": "/api/v1/reviews/written",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "작성한 평가",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "reviews_summary",
    "method": "GET",
    "path": "/api/v1/reviews/summary",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "평가 요약",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "reviews_pending",
    "method": "GET",
    "path": "/api/v1/reviews/pending",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "작성 대기 평가",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "contracts",
    "method": "GET",
    "path": "/api/v1/contracts",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "계약 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "negos_mine",
    "method": "GET",
    "path": "/api/v1/negotiations/mine",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 협상 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "negos_waiting",
    "method": "GET",
    "path": "/api/v1/negotiations/waiting-count",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "대기 협상 수",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "settlements",
    "method": "GET",
    "path": "/api/v1/settlements/mine",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 정산 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "penalties",
    "method": "GET",
    "path": "/api/v1/settlements/penalties/mine",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 위약금",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "pay_methods",
    "method": "GET",
    "path": "/api/v1/accounts/me/payment-methods",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "결제수단",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "withdraw_check",
    "method": "GET",
    "path": "/api/v1/accounts/me/withdrawal-eligibility",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "탈퇴 가능 여부",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cb_suggested",
    "method": "GET",
    "path": "/api/v1/support/chatbot/suggested-questions",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "챗봇 추천 질문",
    "warn": "고정 목록을 반환한다. 순수 오버헤드 측정용 기준선으로 쓸 수 있다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cb_quota",
    "method": "GET",
    "path": "/api/v1/support/chatbot/quota",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "챗봇 잔여 한도",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cb_messages",
    "method": "GET",
    "path": "/api/v1/support/chatbot/messages",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "챗봇 오늘 이력",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "inquiries_mine",
    "method": "GET",
    "path": "/api/v1/support/inquiries/mine",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "내 1:1 문의",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_me",
    "method": "GET",
    "path": "/api/v1/freelancers/me",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "프리랜서 내 정보",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_condition",
    "method": "GET",
    "path": "/api/v1/freelancers/me/condition",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "희망 근무조건",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_resume",
    "method": "GET",
    "path": "/api/v1/freelancers/me/resume",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "이력서",
    "warn": "조인이 많은 편이다. N+1 이 있으면 여기서 드러난다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_resume_draft",
    "method": "GET",
    "path": "/api/v1/freelancers/me/resume/draft",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "이력서 임시저장",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_matching_set",
    "method": "GET",
    "path": "/api/v1/freelancers/me/matching-settings",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "매칭 설정",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "fl_match_recv",
    "method": "GET",
    "path": "/api/v1/matchings/requests/received",
    "target": "spring",
    "auth": "FREELANCER",
    "group": "read",
    "label": "받은 매칭 요청",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cl_me",
    "method": "GET",
    "path": "/api/v1/clients/me",
    "target": "spring",
    "auth": "CLIENT",
    "group": "read",
    "label": "클라이언트 내 정보",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cl_projects",
    "method": "GET",
    "path": "/api/v1/projects/mine",
    "target": "spring",
    "auth": "CLIENT",
    "group": "read",
    "label": "내 프로젝트 목록",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cl_proj_tabs",
    "method": "GET",
    "path": "/api/v1/projects/mine/tab-counts",
    "target": "spring",
    "auth": "CLIENT",
    "group": "read",
    "label": "프로젝트 탭 카운트",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "cl_match_reqs",
    "method": "GET",
    "path": "/api/v1/matchings/requests",
    "target": "spring",
    "auth": "CLIENT",
    "group": "read",
    "label": "보낸 매칭 요청",
    "warn": "",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "chatroom_detail",
    "method": "GET",
    "path": "/api/v1/chat-rooms/{chatRoomId}",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "채팅방 상세",
    "warn": "",
    "needs": [
      "chatRoomId"
    ],
    "hasBody": false
  },
  {
    "key": "chat_messages",
    "method": "GET",
    "path": "/api/v1/chat-rooms/{chatRoomId}/messages",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "채팅 메시지 목록",
    "warn": "페이지네이션 쿼리. 데이터가 쌓이면 여기가 먼저 느려진다",
    "needs": [
      "chatRoomId"
    ],
    "hasBody": false
  },
  {
    "key": "nego_detail",
    "method": "GET",
    "path": "/api/v1/negotiations/{negotiationId}",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "협상 상세",
    "warn": "",
    "needs": [
      "negotiationId"
    ],
    "hasBody": false
  },
  {
    "key": "nego_messages",
    "method": "GET",
    "path": "/api/v1/negotiations/{negotiationId}/messages",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "협상 대화 목록",
    "warn": "",
    "needs": [
      "negotiationId"
    ],
    "hasBody": false
  },
  {
    "key": "contract_detail",
    "method": "GET",
    "path": "/api/v1/contracts/{contractId}",
    "target": "spring",
    "auth": "any",
    "group": "read",
    "label": "계약 상세",
    "warn": "",
    "needs": [
      "contractId"
    ],
    "hasBody": false
  },
  {
    "key": "chat_send",
    "method": "POST",
    "path": "/api/v1/chat-rooms/{chatRoomId}/messages",
    "target": "spring",
    "auth": "any",
    "group": "write",
    "label": "채팅 메시지 전송",
    "warn": "메시지가 실제로 쌓인다. 협상이 끝난 방에서만 열린다(그 전이면 400)",
    "needs": [
      "chatRoomId"
    ],
    "hasBody": true
  },
  {
    "key": "chat_read",
    "method": "POST",
    "path": "/api/v1/chat-rooms/{chatRoomId}/read",
    "target": "spring",
    "auth": "any",
    "group": "write",
    "label": "채팅 읽음 처리",
    "warn": "멱등하지만 UPDATE 가 나간다",
    "needs": [
      "chatRoomId"
    ],
    "hasBody": false
  },
  {
    "key": "notifs_read_all",
    "method": "PUT",
    "path": "/api/v1/notifications/read-all",
    "target": "spring",
    "auth": "any",
    "group": "write",
    "label": "알림 전체 읽음",
    "warn": "첫 호출 이후에는 갱신할 행이 없어서 비용이 급감한다. 처리량 그래프를 오해하기 쉽다",
    "needs": [],
    "hasBody": false
  },
  {
    "key": "nego_read",
    "method": "POST",
    "path": "/api/v1/negotiations/{negotiationId}/read",
    "target": "spring",
    "auth": "any",
    "group": "write",
    "label": "협상 읽음 처리",
    "warn": "",
    "needs": [
      "negotiationId"
    ],
    "hasBody": false
  },
  {
    "key": "ai_chatbot",
    "method": "POST",
    "path": "/api/v1/chatbot/answer",
    "target": "python",
    "auth": "internal",
    "group": "ai",
    "label": "AI 챗봇 답변 생성",
    "warn": "가장 단순한 AI 경로. 상태가 없어서 AI 부하 테스트의 기본으로 쓴다",
    "needs": [],
    "hasBody": true
  },
  {
    "key": "ai_negotiate",
    "method": "POST",
    "path": "/api/v1/negotiations/propose",
    "target": "python",
    "auth": "internal",
    "group": "ai",
    "label": "AI 협상 제안 생성",
    "warn": "프롬프트가 가장 길고 응답도 가장 크다. AI 경로 중 제일 무겁다",
    "needs": [],
    "hasBody": true
  },
  {
    "key": "ai_contract",
    "method": "POST",
    "path": "/api/v1/contracts/draft-texts",
    "target": "python",
    "auth": "internal",
    "group": "ai",
    "label": "AI 계약 문구 생성",
    "warn": "",
    "needs": [],
    "hasBody": true
  },
  {
    "key": "ai_embed_fl",
    "method": "PUT",
    "path": "/api/v1/embeddings/freelancers",
    "target": "python",
    "auth": "internal",
    "group": "ai",
    "label": "AI 프리랜서 임베딩 갱신",
    "warn": "DB 에 벡터를 쓴다. 존재하지 않는 freelancer_id 면 실패할 수 있다",
    "needs": [],
    "hasBody": true
  }
];
window.K6_GROUP_LABELS = {
  "public": "비로그인",
  "read": "조회 (로그인 필요)",
  "write": "쓰기 (데이터가 남는다)",
  "ai": "AI (파이썬 직접)"
};
