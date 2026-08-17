/**
 * API 카탈로그 — 부하 테스트가 부를 수 있는 엔드포인트 목록.
 *
 * 이 파일이 단일 진실 원본이다. main.js 가 여기서 읽어 호출하고, builder/index.html 이
 * 여기서 읽어 체크박스를 그린다. 새 API 를 테스트에 넣으려면 여기 한 줄만 추가한다.
 *
 * ---------------------------------------------------------------------------
 * 필드
 *   key      -e APIS=... 에 쓰는 짧은 이름. 바꾸면 저장해 둔 실행 명령이 깨진다.
 *   method   HTTP 메서드
 *   path     경로. {id} 자리는 런타임에 채운다(needs 참고)
 *   target   'spring' = ALB 를 통해 백엔드로 / 'python' = AI 서버에 직접
 *   auth     'none'     인증 불필요 (GlobalSecurityConfig 의 permitAll 목록)
 *            'any'      로그인만 필요
 *            'CLIENT'   클라이언트 계정만 (@PreAuthorize hasRole)
 *            'FREELANCER' 프리랜서 계정만
 *            'internal' X-Internal-Api-Key (파이썬 내부 호출)
 *   group    'public' | 'read' | 'write' | 'ai'
 *   needs    런타임에 실제 ID 가 필요한 경우. setup() 이 못 찾으면 자동으로 제외된다
 *   body     요청 본문을 만드는 함수. (ctx) => object
 *   warn     부하를 걸 때 주의할 점. 빌더 GUI 가 이 문구를 그대로 보여준다
 *
 * ---------------------------------------------------------------------------
 * name 태그를 왜 경로 템플릿으로 두는가
 *   k6 는 기본적으로 실제 URL 을 name 태그로 쓴다. 그러면 채팅방 ID 마다 시리즈가 하나씩
 *   생겨서 리포트가 수천 줄이 되고 메모리도 그만큼 든다. 템플릿으로 묶으면 시리즈 수가
 *   '엔드포인트 개수'로 고정된다. (파이썬 app/core/metrics.py 의 route_template 과 같은 이유)
 */

/** 카탈로그. 순서가 빌더 GUI 의 표시 순서다. */
export const ENDPOINTS = [
  // =========================================================================
  // 비로그인 (GlobalSecurityConfig permitAll)
  //
  // 토큰이 필요 없어서 계정 수와 무관하게 VU 를 올릴 수 있다. 순수 처리량 상한을 보거나
  // 캐시 효과를 보여줄 때 쓴다. meta/terms 는 값이 바뀌지 않는 마스터 데이터라
  // "캐시 넣기 전/후" 비교에 가장 적합하다.
  // =========================================================================
  { key: 'home_summary',      method: 'GET', path: '/api/v1/home/summary',            target: 'spring', auth: 'none', group: 'public', label: '메인 지표' },
  { key: 'home_reviews',      method: 'GET', path: '/api/v1/home/site-reviews',       target: 'spring', auth: 'none', group: 'public', label: '메인 노출 후기' },
  { key: 'home_faqs',         method: 'GET', path: '/api/v1/home/faqs',               target: 'spring', auth: 'none', group: 'public', label: '메인 FAQ' },
  { key: 'meta_fields',       method: 'GET', path: '/api/v1/meta/business-fields',    target: 'spring', auth: 'none', group: 'public', label: '업종 목록', warn: '불변 마스터 데이터. 캐시 개선 효과를 보여주기 좋다' },
  { key: 'meta_banks',        method: 'GET', path: '/api/v1/meta/banks',              target: 'spring', auth: 'none', group: 'public', label: '은행 목록', warn: '불변 마스터 데이터' },
  { key: 'meta_employees',    method: 'GET', path: '/api/v1/meta/employee-counts',    target: 'spring', auth: 'none', group: 'public', label: '사원수 구간' },
  { key: 'meta_jobcats',      method: 'GET', path: '/api/v1/meta/job-categories',     target: 'spring', auth: 'none', group: 'public', label: '직군 목록', warn: '불변 마스터 데이터' },
  { key: 'meta_jobroles',     method: 'GET', path: '/api/v1/meta/job-roles',          target: 'spring', auth: 'none', group: 'public', label: '직무 목록', warn: '불변 마스터 데이터' },
  { key: 'meta_skills',       method: 'GET', path: '/api/v1/meta/skills',             target: 'spring', auth: 'none', group: 'public', label: '스킬 목록', warn: '목록이 커서 직렬화 비용이 드러난다' },
  { key: 'meta_workcond',     method: 'GET', path: '/api/v1/meta/work-conditions',    target: 'spring', auth: 'none', group: 'public', label: '근무 조건 목록' },
  { key: 'terms_list',        method: 'GET', path: '/api/v1/terms',                   target: 'spring', auth: 'none', group: 'public', label: '약관 목록' },
  { key: 'terms_docs',        method: 'GET', path: '/api/v1/terms/documents',         target: 'spring', auth: 'none', group: 'public', label: '약관 본문' },
  { key: 'grades_table',      method: 'GET', path: '/api/v1/grades',                  target: 'spring', auth: 'none', group: 'public', label: '등급 기준표' },

  // =========================================================================
  // 로그인 후 조회 — 부하 테스트의 주 대상
  //
  // 여기가 HikariCP 풀 대기와 톰캣 스레드 포화가 드러나는 자리다. 대부분 목록 조회라
  // N+1 쿼리가 있으면 VU 를 올릴 때 지연이 계단식으로 튄다.
  // =========================================================================
  { key: 'me',                method: 'GET', path: '/api/v1/auth/me',                          target: 'spring', auth: 'any', group: 'read', label: '내 정보' },
  { key: 'grade_me',          method: 'GET', path: '/api/v1/grades/me',                        target: 'spring', auth: 'any', group: 'read', label: '내 등급' },
  { key: 'notifs',            method: 'GET', path: '/api/v1/notifications',                    target: 'spring', auth: 'any', group: 'read', label: '알림 목록' },
  { key: 'notifs_unread',     method: 'GET', path: '/api/v1/notifications/unread-count',        target: 'spring', auth: 'any', group: 'read', label: '안 읽은 알림 수', warn: '프론트가 폴링하는 경로. 실제 트래픽 비중이 가장 높다' },
  { key: 'chatrooms',         method: 'GET', path: '/api/v1/chat-rooms',                       target: 'spring', auth: 'any', group: 'read', label: '채팅방 목록' },
  { key: 'chat_unread',       method: 'GET', path: '/api/v1/chat-rooms/unread-count',           target: 'spring', auth: 'any', group: 'read', label: '안 읽은 메시지 수', warn: '프론트가 폴링하는 경로' },
  { key: 'reviews_recv',      method: 'GET', path: '/api/v1/reviews/received',                 target: 'spring', auth: 'any', group: 'read', label: '받은 평가' },
  { key: 'reviews_written',   method: 'GET', path: '/api/v1/reviews/written',                  target: 'spring', auth: 'any', group: 'read', label: '작성한 평가' },
  { key: 'reviews_summary',   method: 'GET', path: '/api/v1/reviews/summary',                  target: 'spring', auth: 'any', group: 'read', label: '평가 요약' },
  { key: 'reviews_pending',   method: 'GET', path: '/api/v1/reviews/pending',                  target: 'spring', auth: 'any', group: 'read', label: '작성 대기 평가' },
  { key: 'contracts',         method: 'GET', path: '/api/v1/contracts',                        target: 'spring', auth: 'any', group: 'read', label: '계약 목록' },
  { key: 'negos_mine',        method: 'GET', path: '/api/v1/negotiations/mine',                 target: 'spring', auth: 'any', group: 'read', label: '내 협상 목록' },
  { key: 'negos_waiting',     method: 'GET', path: '/api/v1/negotiations/waiting-count',        target: 'spring', auth: 'any', group: 'read', label: '대기 협상 수' },
  { key: 'settlements',       method: 'GET', path: '/api/v1/settlements/mine',                 target: 'spring', auth: 'any', group: 'read', label: '내 정산 목록' },
  { key: 'penalties',         method: 'GET', path: '/api/v1/settlements/penalties/mine',       target: 'spring', auth: 'any', group: 'read', label: '내 위약금' },
  { key: 'pay_methods',       method: 'GET', path: '/api/v1/accounts/me/payment-methods',      target: 'spring', auth: 'any', group: 'read', label: '결제수단' },
  { key: 'withdraw_check',    method: 'GET', path: '/api/v1/accounts/me/withdrawal-eligibility', target: 'spring', auth: 'any', group: 'read', label: '탈퇴 가능 여부' },
  { key: 'cb_suggested',      method: 'GET', path: '/api/v1/support/chatbot/suggested-questions', target: 'spring', auth: 'any', group: 'read', label: '챗봇 추천 질문', warn: '고정 목록을 반환한다. 순수 오버헤드 측정용 기준선으로 쓸 수 있다' },
  { key: 'cb_quota',          method: 'GET', path: '/api/v1/support/chatbot/quota',            target: 'spring', auth: 'any', group: 'read', label: '챗봇 잔여 한도' },
  { key: 'cb_messages',       method: 'GET', path: '/api/v1/support/chatbot/messages',         target: 'spring', auth: 'any', group: 'read', label: '챗봇 오늘 이력' },
  { key: 'inquiries_mine',    method: 'GET', path: '/api/v1/support/inquiries/mine',           target: 'spring', auth: 'any', group: 'read', label: '내 1:1 문의' },

  // ---- 프리랜서 계정만 -----------------------------------------------------
  { key: 'fl_me',             method: 'GET', path: '/api/v1/freelancers/me',                   target: 'spring', auth: 'FREELANCER', group: 'read', label: '프리랜서 내 정보' },
  { key: 'fl_condition',      method: 'GET', path: '/api/v1/freelancers/me/condition',         target: 'spring', auth: 'FREELANCER', group: 'read', label: '희망 근무조건' },
  { key: 'fl_resume',         method: 'GET', path: '/api/v1/freelancers/me/resume',            target: 'spring', auth: 'FREELANCER', group: 'read', label: '이력서', warn: '조인이 많은 편이다. N+1 이 있으면 여기서 드러난다' },
  { key: 'fl_resume_draft',   method: 'GET', path: '/api/v1/freelancers/me/resume/draft',      target: 'spring', auth: 'FREELANCER', group: 'read', label: '이력서 임시저장' },
  { key: 'fl_matching_set',   method: 'GET', path: '/api/v1/freelancers/me/matching-settings', target: 'spring', auth: 'FREELANCER', group: 'read', label: '매칭 설정' },
  { key: 'fl_match_recv',     method: 'GET', path: '/api/v1/matchings/requests/received',      target: 'spring', auth: 'FREELANCER', group: 'read', label: '받은 매칭 요청' },

  // ---- 클라이언트 계정만 ---------------------------------------------------
  { key: 'cl_me',             method: 'GET', path: '/api/v1/clients/me',                       target: 'spring', auth: 'CLIENT', group: 'read', label: '클라이언트 내 정보' },
  { key: 'cl_projects',       method: 'GET', path: '/api/v1/projects/mine',                    target: 'spring', auth: 'CLIENT', group: 'read', label: '내 프로젝트 목록' },
  { key: 'cl_proj_tabs',      method: 'GET', path: '/api/v1/projects/mine/tab-counts',         target: 'spring', auth: 'CLIENT', group: 'read', label: '프로젝트 탭 카운트' },
  { key: 'cl_match_reqs',     method: 'GET', path: '/api/v1/matchings/requests',               target: 'spring', auth: 'CLIENT', group: 'read', label: '보낸 매칭 요청' },
  {
    key: 'proj_prereview', method: 'POST', path: '/api/v1/projects/pre-review',
    target: 'spring', auth: 'CLIENT', group: 'read',
    label: '프로젝트 사전 검수',
    warn: 'POST 지만 쓰기가 없다(readOnly 트랜잭션). 포지션 수만큼 집계 쿼리가 나가는 자리다',
    body: () => ({
      positions: [
        { jobRole: 'BACKEND',  headcount: 2, skills: ['JAVA', 'SPRING_BOOT'] },
        { jobRole: 'FRONTEND', headcount: 2, skills: ['REACT', 'TYPESCRIPT'] },
        { jobRole: 'DEVOPS',   headcount: 1, skills: ['AWS', 'DOCKER'] },
      ],
    }),
  },
  // ---- 실제 ID 가 필요한 조회 ----------------------------------------------
  // setup() 이 목록 API 로 ID 를 찾아 채운다. 데이터가 없으면 이 항목들은 자동으로 빠진다.
  { key: 'chatroom_detail',   method: 'GET', path: '/api/v1/chat-rooms/{chatRoomId}',          target: 'spring', auth: 'any', group: 'read', needs: ['chatRoomId'], label: '채팅방 상세' },
  { key: 'chat_messages',     method: 'GET', path: '/api/v1/chat-rooms/{chatRoomId}/messages', target: 'spring', auth: 'any', group: 'read', needs: ['chatRoomId'], label: '채팅 메시지 목록', warn: '페이지네이션 쿼리. 데이터가 쌓이면 여기가 먼저 느려진다' },
  { key: 'nego_detail',       method: 'GET', path: '/api/v1/negotiations/{negotiationId}',     target: 'spring', auth: 'any', group: 'read', needs: ['negotiationId'], label: '협상 상세' },
  { key: 'nego_messages',     method: 'GET', path: '/api/v1/negotiations/{negotiationId}/messages', target: 'spring', auth: 'any', group: 'read', needs: ['negotiationId'], label: '협상 대화 목록' },
  { key: 'contract_detail',   method: 'GET', path: '/api/v1/contracts/{contractId}',           target: 'spring', auth: 'any', group: 'read', needs: ['contractId'], label: '계약 상세' },

  // =========================================================================
  // 쓰기
  //
  // DB 쓰기 경합과 트랜잭션 지연을 본다. 데이터가 실제로 남으므로 운영 환경에는 쓰지 않는다.
  // =========================================================================
  {
    key: 'chat_send', method: 'POST', path: '/api/v1/chat-rooms/{chatRoomId}/messages',
    target: 'spring', auth: 'any', group: 'write', needs: ['chatRoomId'],
    label: '채팅 메시지 전송',
    warn: '메시지가 실제로 쌓인다. 협상이 끝난 방에서만 열린다(그 전이면 400)',
    body: (ctx) => ({ content: `[k6 ${ctx.testid}] 부하 테스트 메시지 ${ctx.seq}` }),
  },
  {
    key: 'chat_read', method: 'POST', path: '/api/v1/chat-rooms/{chatRoomId}/read',
    target: 'spring', auth: 'any', group: 'write', needs: ['chatRoomId'],
    label: '채팅 읽음 처리',
    warn: '멱등하지만 UPDATE 가 나간다',
  },
  {
    key: 'notifs_read_all', method: 'PUT', path: '/api/v1/notifications/read-all',
    target: 'spring', auth: 'any', group: 'write',
    label: '알림 전체 읽음',
    warn: '첫 호출 이후에는 갱신할 행이 없어서 비용이 급감한다. 처리량 그래프를 오해하기 쉽다',
  },
  {
    key: 'nego_read', method: 'POST', path: '/api/v1/negotiations/{negotiationId}/read',
    target: 'spring', auth: 'any', group: 'write', needs: ['negotiationId'],
    label: '협상 읽음 처리',
  },

  // =========================================================================
  // AI — 파이썬 서버에 직접 부하를 건다
  //
  // ★ 스프링 경유로는 AI 부하 테스트가 안 된다. 스프링이 쿼터를 막는다.
  //     - 챗봇: 계정당 하루 10회 (SupportController "하루 10회를 넘기면 429")
  //     - 재추천: 무료 횟수 제한
  //     - 협상 응답: 라운드 상태 기계에 묶여 있어 반복 호출 불가
  //   쿼터는 스프링에 있고 LLM 호출은 파이썬에 있어서, AI 스텁을 켜도 스프링 쿼터는 그대로다.
  //   그래서 AI 용량은 파이썬을 직접 때려야 측정된다. 계측을 넣은 서버도 이쪽이다.
  //
  // ★ 반드시 AI_STUB_MODE=true 로 띄운 파이썬을 대상으로 한다. 아니면 실제 Gemini 쿼터가
  //   소진되고 키가 전부 쿨다운에 들어가 그 시점부터 전부 실패한다(측정 불가 + 비용).
  //   확인: curl -s <AI_BASE_URL>/metrics | grep gemini_stub_mode   -> 1 이어야 한다
  //
  // 인증은 X-Internal-Api-Key 다. 파이썬은 VPC 내부 전용이라 로컬에서 부하를 걸려면
  // docker-compose 로 띄우거나 포트포워딩이 필요하다(README 참고).
  // =========================================================================
  {
    key: 'ai_chatbot', method: 'POST', path: '/api/v1/chatbot/answer',
    target: 'python', auth: 'internal', group: 'ai',
    label: 'AI 챗봇 답변 생성',
    warn: '가장 단순한 AI 경로. 상태가 없어서 AI 부하 테스트의 기본으로 쓴다',
    body: (ctx) => ({ question: AI_QUESTIONS[ctx.seq % AI_QUESTIONS.length] }),
  },
  {
    key: 'ai_negotiate', method: 'POST', path: '/api/v1/negotiations/propose',
    target: 'python', auth: 'internal', group: 'ai',
    label: 'AI 협상 제안 생성',
    warn: '프롬프트가 가장 길고 응답도 가장 크다. AI 경로 중 제일 무겁다',
    body: (ctx) => ({
      negotiation_id: 900000 + (ctx.seq % 1000),
      round: 1,
      budget_cap: 5000000,
      conditions: [
        {
          condition_id: 401, type: 'AMOUNT',
          client_value: '4000000', freelancer_value: '6000000',
          client_floor: '4500000', freelancer_floor: '5500000',
          value_format: '숫자만',
        },
        {
          condition_id: 402, type: 'WORK_STYLE',
          client_value: 'ONSITE', freelancer_value: 'REMOTE',
          allowed_values: ['REMOTE', 'ONSITE', 'ANY'],
        },
        {
          condition_id: 403, type: 'PERIOD',
          client_value: '3', freelancer_value: '6',
          value_format: '<숫자> MONTH',
        },
      ],
    }),
  },
  {
    key: 'ai_contract', method: 'POST', path: '/api/v1/contracts/draft-texts',
    target: 'python', auth: 'internal', group: 'ai',
    label: 'AI 계약 문구 생성',
    body: (ctx) => ({
      contract_id: 900000 + (ctx.seq % 1000),
      main_task: '백엔드 API 개발 및 운영',
      detail_scope: '주문/결제 도메인 신규 기능 개발, 기존 API 성능 개선',
      agreed_notes: ['주 2회 정기 미팅', '원격 근무 가능'],
    }),
  },
  {
    key: 'ai_embed_fl', method: 'PUT', path: '/api/v1/embeddings/freelancers',
    target: 'python', auth: 'internal', group: 'ai',
    label: 'AI 프리랜서 임베딩 갱신',
    warn: 'DB 에 벡터를 쓴다. 존재하지 않는 freelancer_id 면 실패할 수 있다',
    body: (ctx) => ({ freelancer_id: 1, text: `k6 부하 테스트 이력서 ${ctx.seq}` }),
  },
];

/** AI 챗봇 시나리오가 돌려 쓰는 질문. 같은 질문만 보내면 캐시가 있을 때 결과가 왜곡된다. */
const AI_QUESTIONS = [
  '프로젝트는 어떻게 등록하나요?',
  '착수금 수수료는 언제 결제하나요?',
  '무료 재추천은 몇 번까지 쓸 수 있나요?',
  '협상이 결렬되면 어떻게 되나요?',
  '계약서에 서명하는 방법을 알려주세요',
  '정산은 프로젝트가 끝나고 얼마 뒤에 되나요?',
  '탈퇴하려면 어떻게 해야 하나요?',
  '리뷰는 언제 작성할 수 있나요?',
];

/** 그룹 표시 이름. 빌더 GUI 와 리포트에서 같은 말을 쓰기 위해 여기 둔다. */
export const GROUP_LABELS = {
  public: '비로그인',
  read: '조회 (로그인 필요)',
  write: '쓰기 (데이터가 남는다)',
  ai: 'AI (파이썬 직접)',
};

/** key -> 엔드포인트. 잘못된 key 를 즉시 잡기 위해 조회 실패를 예외로 만든다. */
export function findEndpoint(key) {
  const found = ENDPOINTS.filter((e) => e.key === key);
  if (found.length === 0) {
    throw new Error(
      `알 수 없는 API key: "${key}"\n` +
        `endpoints.js 에 없는 이름이다. 사용 가능한 key:\n  ${ENDPOINTS.map((e) => e.key).join(', ')}`
    );
  }
  return found[0];
}

/** 경로의 {placeholder} 를 채운다. 못 채우면 null 을 준다(호출을 건너뛰라는 뜻). */
export function resolvePath(endpoint, ids) {
  let path = endpoint.path;
  for (const need of endpoint.needs || []) {
    const value = ids[need];
    if (value === undefined || value === null) return null;
    path = path.replace(`{${need}}`, String(value));
  }
  return path;
}
