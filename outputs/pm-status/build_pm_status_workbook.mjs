import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "C:/52_Pairing/Pairing-backend/outputs/pm-status";
const workbook = Workbook.create();

const today = "2026-08-07";

const statuses = [
  "완료",
  "부분완료",
  "개발중",
  "스텁/API계약",
  "미시작",
  "막힘",
  "확인필요",
];

const featureRows = [
  {
    no: "01",
    role: "1번",
    owner: "회원/인증 담당",
    domain: "Auth",
    feature: "회원가입/로그인/소셜/이메일 인증/계정 복구",
    api: "POST /auth/login, /signup/*, /email-verifications, /password/*",
    status: "완료",
    percent: 90,
    backend: "서비스/도메인/인프라/테스트 존재. 인증 흐름 통합 테스트 포함.",
    done: "회원가입, 로그인, 토큰 재발급/로그아웃, 이메일 인증, 비밀번호 재설정, 계정 잠금 해제.",
    todo: "운영 SMTP/소셜 키 실환경 검증, FE 최종 연동 QA.",
    blocker: "",
    feNeed: "로그인/회원가입 화면 연동 가능. 쿠키/401 처리 확인.",
    evidence: "src/main/java/com/pairing/auth, src/test/java/com/pairing/auth",
  },
  {
    no: "02",
    role: "공통",
    owner: "메타/약관 담당",
    domain: "Meta/Terms",
    feature: "공통 코드/약관 조회",
    api: "GET /meta/*, GET /terms",
    status: "완료",
    percent: 90,
    backend: "컨트롤러와 서비스 구현. 직군/직무/스킬/근무조건/약관 조회 가능.",
    done: "프론트 입력폼에 필요한 코드 API 제공.",
    todo: "마스터 데이터 라벨 최종 검수.",
    blocker: "",
    feNeed: "회원가입, 프리랜서 조건, 프로젝트 등록 화면에서 바로 사용.",
    evidence: "src/main/java/com/pairing/meta, src/main/java/com/pairing/terms",
  },
  {
    no: "03",
    role: "공통",
    owner: "파일 담당",
    domain: "File",
    feature: "파일 업로드/조회/삭제",
    api: "POST /files, GET /files/{id}, DELETE /files/{id}",
    status: "스텁/API계약",
    percent: 25,
    backend: "API 계약과 컨트롤러는 있으나 TODO 주석 기준 실제 저장/권한 로직 미구현.",
    done: "엔드포인트와 응답 DTO 계약.",
    todo: "S3 업로드, file 테이블 저장, 접근 권한, 삭제 처리.",
    blocker: "S3 설정/파일 도메인 구현 필요.",
    feNeed: "프론트는 fileId만 후속 API에 전달하도록 계약 가능.",
    evidence: "src/main/java/com/pairing/file/presentation/api/FileController.java",
  },
  {
    no: "04",
    role: "공통",
    owner: "홈/등급 담당",
    domain: "Home/Grade",
    feature: "메인 요약/FAQ/등급 조회",
    api: "GET /home/*, GET /grades, GET /grades/me",
    status: "스텁/API계약",
    percent: 30,
    backend: "컨트롤러와 DTO 계약 중심. 집계/등급 산정 TODO 존재.",
    done: "화면 계약과 응답 형태.",
    todo: "집계 쿼리, 등급 정책 계산, 캐시 여부 결정.",
    blocker: "리뷰/계약/정산 데이터 연동 필요.",
    feNeed: "메인 화면은 더미/스텁으로 선연동 가능.",
    evidence: "src/main/java/com/pairing/home, src/main/java/com/pairing/grade",
  },
  {
    no: "05",
    role: "공통/정산",
    owner: "계정/결제수단 담당",
    domain: "Account",
    feature: "결제수단/탈퇴/관리자 회원 관리",
    api: "GET/POST/PUT/DELETE /accounts/me/payment-methods, DELETE /accounts/me, /accounts/admin/*",
    status: "부분완료",
    percent: 55,
    backend: "계정 도메인/결제수단 저장 테스트 일부 존재. 컨트롤러 TODO 다수.",
    done: "Account 도메인 모델, 결제수단 영속성 테스트.",
    todo: "결제수단 CRUD 서비스, 탈퇴 제한, 관리자 조회/정지.",
    blocker: "정산/프로젝트 진행 상태와 탈퇴 제한 연동 필요.",
    feNeed: "마이페이지 결제수단 화면은 API 구현 일정 확인 필요.",
    evidence: "src/main/java/com/pairing/account, src/test/java/com/pairing/account",
  },
  {
    no: "06",
    role: "21번",
    owner: "클라이언트 담당",
    domain: "Client",
    feature: "클라이언트 마이페이지/프로필 수정",
    api: "GET /clients/me, PATCH /clients/me",
    status: "스텁/API계약",
    percent: 25,
    backend: "컨트롤러와 DTO 계약은 있으나 계정+프로필+리뷰 집계 TODO.",
    done: "엔드포인트와 응답 형태.",
    todo: "ClientProfile 조회/수정, 이메일 인증 마커, 리뷰/등급 집계.",
    blocker: "파일/리뷰/계정 연동 필요.",
    feNeed: "마이페이지 UI 더미 연동 가능, 실제 저장은 구현 후.",
    evidence: "src/main/java/com/pairing/client/presentation/api/ClientController.java",
  },
  {
    no: "07",
    role: "2번",
    owner: "프리랜서 담당",
    domain: "Freelancer",
    feature: "프리랜서 조건/이력서/포트폴리오/매칭설정",
    api: "GET/PUT /freelancers/me/condition, /resume, /portfolios, /matching-settings",
    status: "스텁/API계약",
    percent: 30,
    backend: "API 계약은 풍부하나 컨트롤러 TODO 기준 실제 DB 조회/저장 미구현.",
    done: "조건/이력서/포트폴리오/매칭설정 API 계약.",
    todo: "freelancer_condition/resume 저장, 임베딩 갱신 호출, 후보 카드 데이터 제공 포트 구현.",
    blocker: "AI 매칭 후보 카드와 임베딩 트리거가 이 도메인에 의존.",
    feNeed: "프리랜서 등록/마이페이지 화면은 DTO 확정 후 더미 연동 가능.",
    evidence: "src/main/java/com/pairing/freelancer/presentation/api/FreelancerController.java",
  },
  {
    no: "08",
    role: "3번",
    owner: "프로젝트 담당",
    domain: "Project",
    feature: "프로젝트 등록/검수/목록/상태 변경/관리자",
    api: "POST /projects, /pre-review, GET /projects/mine, PUT /projects/{id}, admin",
    status: "스텁/API계약",
    percent: 30,
    backend: "컨트롤러와 API 계약 존재. 프로젝트+포지션+스킬 저장/검수/상태 전이 TODO.",
    done: "프로젝트 API 계약과 DTO.",
    todo: "프로젝트 저장, 포지션 저장, 검수 통과 시 포지션 임베딩 호출, 결제 완료 후 매칭 라운드 트리거.",
    blocker: "AI 매칭과 정산/결제 시작점이 프로젝트 도메인에 의존.",
    feNeed: "프로젝트 등록 화면 DTO는 확정 가능. 실제 매칭 시작 연동 일정 필요.",
    evidence: "src/main/java/com/pairing/project/presentation/api/ProjectController.java",
  },
  {
    no: "09",
    role: "4번",
    owner: "AI 매칭 담당",
    domain: "Matching",
    feature: "후보 추천/거절/재추천/요청/수락/거절",
    api: "GET /matchings/positions/{id}/candidates, POST /requests, /rerecommendations, /acceptance",
    status: "부분완료",
    percent: 75,
    backend: "매칭 도메인 9개 API, DB 연동, Python adapter, 서킷브레이커/레이트리밋 구현. 외부 도메인은 스텁.",
    done: "라운드/후보/요청/스냅샷 저장, 재추천 제한, fitScore 제거, 테스트 83개 통과 기록.",
    todo: "Pairing-python 프롬프트/필터, Stage F 가드, 스텁 3개 교체, 최초 라운드 결제 트리거.",
    blocker: "프리랜서/프로젝트/협상 도메인 실제 구현 필요.",
    feNeed: "후보/요청 화면 API 선연동 가능. 이름/사진/등급/평점은 스텁 값일 수 있음.",
    evidence: "src/main/java/com/pairing/matching, docs/personal/ai-matching-notes.md",
  },
  {
    no: "10",
    role: "5번",
    owner: "A2A 협상 담당",
    domain: "Negotiation",
    feature: "협상 목록/상세/메시지/시작/응답/최종승인/포기/관리자",
    api: "GET/POST /negotiations/*",
    status: "스텁/API계약",
    percent: 25,
    backend: "프레젠테이션 계층과 API 계약 중심. application/usecase 부재. 매칭은 StubNegotiationAdapter로 임시 연결.",
    done: "협상 API 계약, 컨트롤러.",
    todo: "NegotiationCommandUseCase, 협상 조건/메시지 저장, AI 협상 호출, 매칭 수락 연동.",
    blocker: "매칭 수락 시 생성 필드 minAcceptAmount/PERIOD 확인 필요.",
    feNeed: "협상 화면은 계약 확인 후 더미 연동 가능. 매칭 수락 플로우와 일정 조율 필수.",
    evidence: "src/main/java/com/pairing/negotiation, .ai/HANDOFF.md",
  },
  {
    no: "11",
    role: "6번",
    owner: "채팅 담당",
    domain: "Chat",
    feature: "협상 후 채팅방/메시지/읽음/나가기",
    api: "GET/POST /chat-rooms/*",
    status: "스텁/API계약",
    percent: 20,
    backend: "컨트롤러 TODO 기준 실제 메시지 저장/STOMP 연동 미구현.",
    done: "REST 이력 조회/전송 계약.",
    todo: "chat_room_member 권한, 메시지 저장, STOMP 브로드캐스트, unread count.",
    blocker: "협상 성사 후 방 생성 조건 필요.",
    feNeed: "채팅 UI는 REST/STOMP 계약 확정 필요.",
    evidence: "src/main/java/com/pairing/chat/presentation/api/ChatController.java",
  },
  {
    no: "12",
    role: "7번",
    owner: "계약 담당",
    domain: "Contract",
    feature: "계약 조회/PDF/서명/거절/완료/중도종료",
    api: "GET/POST /contracts/*",
    status: "스텁/API계약",
    percent: 20,
    backend: "계약 API 계약과 컨트롤러 존재. 실제 계약 생성/서명/정산 연결 TODO.",
    done: "계약 화면 API 계약.",
    todo: "협상 결과 기반 계약 생성, PDF, 서명 상태 전이, 정산 생성.",
    blocker: "협상 완료 데이터 필요.",
    feNeed: "계약 상세/서명 모달은 DTO 확정 후 더미 연동 가능.",
    evidence: "src/main/java/com/pairing/contract/presentation/api/ContractController.java",
  },
  {
    no: "13",
    role: "8번",
    owner: "정산 담당",
    domain: "Settlement",
    feature: "정산/위약금/결제/관리자 정산",
    api: "GET/POST /settlements/*",
    status: "스텁/API계약",
    percent: 20,
    backend: "컨트롤러/API 계약 존재. 실제 결제/원장/수수료 계산 TODO.",
    done: "정산 화면 API 계약.",
    todo: "착수금/성공보수/위약금 생성, 결제수단 연동, 미납 처리.",
    blocker: "프로젝트/계약 상태 전이와 결제수단 구현 필요.",
    feNeed: "결제 화면은 결제수단 API와 함께 연동 일정 필요.",
    evidence: "src/main/java/com/pairing/settlement/presentation/api",
  },
  {
    no: "14",
    role: "9번",
    owner: "리뷰 담당",
    domain: "Review",
    feature: "상호 리뷰/사이트 리뷰/관리자 공개 설정",
    api: "GET/POST/PUT /reviews/*",
    status: "스텁/API계약",
    percent: 20,
    backend: "컨트롤러/API 계약 존재. 리뷰 저장/집계/등급 연동 TODO.",
    done: "리뷰 API 계약.",
    todo: "계약 종료 검증, 중복 차단, 리뷰 집계, 등급 반영.",
    blocker: "계약/정산 완료 데이터 필요.",
    feNeed: "리뷰 작성/목록/관리자 사이트 리뷰 화면 계약 가능.",
    evidence: "src/main/java/com/pairing/review/presentation/api/ReviewController.java",
  },
  {
    no: "15",
    role: "10번",
    owner: "알림/고객지원 담당",
    domain: "Notification/Support",
    feature: "알림/챗봇/1:1 문의/관리자 답변",
    api: "GET/PUT/DELETE /notifications, GET/POST /support/*",
    status: "스텁/API계약",
    percent: 20,
    backend: "패키지/컨트롤러 계약 중심. 알림 발생/챗봇/문의 저장 TODO.",
    done: "지원/알림 API 계약.",
    todo: "알림 생성 이벤트, 문의 저장/답변, 챗봇 quota/AI 호출.",
    blocker: "각 도메인 이벤트 연동 필요.",
    feNeed: "알림센터/문의 화면은 DTO 기준 선작업 가능.",
    evidence: "src/main/java/com/pairing/notification, src/main/java/com/pairing/support",
  },
  {
    no: "16",
    role: "11번",
    owner: "관리자 담당",
    domain: "Admin",
    feature: "관리자 대시보드",
    api: "GET /admin/dashboard",
    status: "스텁/API계약",
    percent: 20,
    backend: "컨트롤러와 응답 계약. 도메인별 집계 TODO.",
    done: "대시보드 API 계약.",
    todo: "회원/프로젝트/협상/정산 집계 쿼리 연결.",
    blocker: "각 도메인 데이터 구현 이후 집계 가능.",
    feNeed: "관리자 대시보드 더미 연동 가능.",
    evidence: "src/main/java/com/pairing/admin/presentation/api/AdminDashboardController.java",
  },
  {
    no: "17",
    role: "공통",
    owner: "인프라/보안 담당",
    domain: "Global/Infra",
    feature: "보안/JWT/Redis/S3/CORS/WebSocket/RateLimit/CircuitBreaker",
    api: "공통 설정",
    status: "부분완료",
    percent: 70,
    backend: "JWT, Security, Redis, S3, WebSocket, AOP, 매칭 rate limit/circuit breaker 존재.",
    done: "공통 인증/예외/응답/로깅 기반, 로컬 설정.",
    todo: "운영 환경변수, 실제 S3/SMTP/AI 서버 연결 검증.",
    blocker: "외부 키/운영 인프라 필요.",
    feNeed: "CORS/쿠키/401/403 응답 처리 기준 공유.",
    evidence: "src/main/java/com/pairing/global, application.yaml",
  },
];

const roleRows = [
  ["1번", "회원/인증", "Auth", "완료에 가까움", "로그인/회원가입/인증/계정복구"],
  ["2번", "프리랜서", "Freelancer", "스텁/API계약", "조건/이력서/포트폴리오/매칭설정. 4번 매칭 후보 카드 의존."],
  ["3번", "프로젝트", "Project", "스텁/API계약", "프로젝트 등록/검수/포지션. 4번 매칭 시작 트리거 의존."],
  ["4번", "AI 매칭", "Matching + Pairing-python", "부분완료", "백엔드 매칭 코어 완료, Python 프롬프트/가드/스텁 교체 남음."],
  ["5번", "A2A 협상", "Negotiation", "스텁/API계약", "수락 시 협상방 생성 UseCase 필요. minAcceptAmount/PERIOD 확인 필요."],
  ["6번", "채팅", "Chat", "스텁/API계약", "협상 성사 후 채팅방/메시지."],
  ["7번", "계약", "Contract", "스텁/API계약", "협상 결과 기반 계약/PDF/서명."],
  ["8번", "정산/결제", "Settlement + Account payment", "스텁/API계약", "착수금/성공보수/위약금/결제수단."],
  ["9번", "리뷰/등급", "Review + Grade", "스텁/API계약", "리뷰 집계와 등급 산정."],
  ["10번", "알림/지원", "Notification + Support", "스텁/API계약", "알림 이벤트, 챗봇, 문의."],
  ["11번", "관리자", "Admin", "스텁/API계약", "관리자 대시보드/집계."],
  ["공통", "메타/약관/파일/인프라", "Meta/Terms/File/Global", "혼재", "프론트 공통 코드와 파일/보안/환경 설정."],
];

const questions = [
  ["5번 협상", "minAcceptAmount 단위", "월단가(원) 기준으로 확정. payUnit 환산 없이 floor에 그대로 사용.", "확인 완료", "협상 담당자에게 공유"],
  ["5번 협상", "PERIOD 전달", "periodValue/periodUnit은 nullable. 값 있으면 협상 대상, null이면 제외.", "확인 필요", "협상 DTO 반영 여부 확인"],
  ["2번 프리랜서", "후보 카드 데이터", "이름/사진/등급/평점/조건/스킬을 Matching의 FreelancerDirectoryPort로 제공해야 함.", "확인 필요", "실제 구현 일정 받기"],
  ["3번 프로젝트", "포지션/프로젝트 요약", "프로젝트 소유권, headcount, budgetAmount, position summary를 ProjectDirectoryPort로 제공해야 함.", "확인 필요", "실제 구현 일정 받기"],
  ["3번/8번", "최초 매칭 트리거", "검수 통과 시 포지션 임베딩, 착수금 결제 완료 시 MatchingRoundCreationService 호출.", "확인 필요", "정산/프로젝트 플로우 일정 조율"],
  ["4번 매칭", "Pairing-python 품질", "_build_prompt, 하드필터, Stage F 가드 남음.", "진행 예정", "3일차 작업으로 배정"],
  ["프론트 PM", "fitScore 제거", "점수 숫자는 API/화면에 노출하지 않고 lowScoreWarned 배너만 사용.", "공유 필요", "Figma의 AI 점수 배지 제거 요청"],
  ["전체", "일일 보고 방식", "퍼센트는 참고용. 오늘 한 일/막힘/내일 할 일/연동 변경 중심.", "도입 제안", "팀 채팅 고정 양식으로 공유"],
];

const scheduleRows = [
  ["2026-08-07", "백엔드 현황 정리", "전체", "PM", "진행중", "이 엑셀로 1차 확인"],
  ["2026-08-08", "담당자별 상태 확인", "전체", "각 담당", "예정", "역할번호/상태/막힘 수정"],
  ["2026-08-08", "프론트 PM API 우선순위 공유", "FE/BE", "PM+FE PM", "예정", "DTO 확정일과 API 완료일 분리"],
  ["2026-08-09", "프리랜서/프로젝트 실제 저장 API 우선 구현", "2번/3번", "각 담당", "예정", "매칭 스텁 제거 선행"],
  ["2026-08-09", "협상 생성 UseCase 계약 확정", "4번/5번", "매칭+협상", "예정", "수락 플로우 연동"],
  ["2026-08-10", "매칭 Python 프롬프트/필터/가드", "4번", "AI 매칭", "예정", "Pairing-python 작업"],
  ["2026-08-10", "주요 화면 더미 연동 시작", "FE", "프론트 PM", "예정", "Auth/Meta/Terms/Matching 선연동 가능"],
  ["2026-08-11", "통합 테스트 1차", "BE/FE", "전체", "예정", "로그인→프로젝트→매칭→협상 진입"],
];

function addTitle(sheet, title, subtitle, endCol = "L") {
  sheet.getRange(`A1:${endCol}1`).merge();
  sheet.getRange("A1").values = [[title]];
  sheet.getRange(`A2:${endCol}2`).merge();
  sheet.getRange("A2").values = [[subtitle]];
  sheet.getRange(`A1:${endCol}1`).format = {
    fill: "#12343B",
    font: { bold: true, color: "#FFFFFF", size: 16 },
  };
  sheet.getRange(`A2:${endCol}2`).format = {
    fill: "#E8F3F1",
    font: { color: "#12343B", italic: true },
  };
}

function styleHeader(range) {
  range.format = {
    fill: "#2D5A63",
    font: { bold: true, color: "#FFFFFF" },
    wrapText: true,
  };
}

function applyTableStyle(sheet, rangeAddress) {
  const range = sheet.getRange(rangeAddress);
  range.format.borders = { preset: "all", style: "thin", color: "#D9E2E1" };
  range.format.wrapText = true;
}

const summary = workbook.worksheets.add("요약");
addTitle(summary, "Pairing Backend PM 현황 요약", `기준일: ${today} / 코드와 md 작업노트 기준 1차 정리`, "H");
summary.getRange("A4:H4").values = [["구분", "개수", "평균 진행률", "완료/부분완료", "스텁/API계약", "막힘/확인필요", "PM 메모", "다음 액션"]];
styleHeader(summary.getRange("A4:H4"));
const total = featureRows.length;
const avg = Math.round(featureRows.reduce((sum, row) => sum + row.percent, 0) / total);
const doneCount = featureRows.filter((r) => r.status === "완료" || r.status === "부분완료").length;
const stubCount = featureRows.filter((r) => r.status === "스텁/API계약").length;
const riskCount = featureRows.filter((r) => r.status === "막힘" || r.status === "확인필요" || r.blocker).length;
summary.getRange("A5:H8").values = [
  ["전체 백엔드 기능", total, `${avg}%`, doneCount, stubCount, riskCount, "API 계약은 넓게 잡혀 있고 실제 구현은 도메인별 편차 큼", "담당자별 실제 구현 완료일 확인"],
  ["프론트 선연동 가능", 5, "", "Auth/Meta/Terms/Matching 일부", "Home/File 등 더미 가능", "", "DTO 확정된 영역부터 더미 연동", "FE PM과 API 우선순위 합의"],
  ["핵심 병목", 3, "", "", "", "프리랜서/프로젝트/협상 스텁", "매칭 4번이 2/3/5번 실제 구현에 의존", "각 담당에게 제공 포트/UseCase 일정 확인"],
  ["오늘 공유 포인트", "", "", "", "", "", "퍼센트보다 막힘/연동일/DTO 변경 중심으로 매일 공유", "일일보고양식 시트 사용"],
];
applyTableStyle(summary, "A4:H8");
summary.getRange("B5:F8").format = { horizontalAlignment: "center" };
summary.getRange("C5:C8").format = { horizontalAlignment: "center" };
summary.getRange("A11:C18").values = [
  ["상태", "개수", "설명"],
  ["완료", featureRows.filter((r) => r.status === "완료").length, "실제 서비스/테스트까지 상당 부분 구현"],
  ["부분완료", featureRows.filter((r) => r.status === "부분완료").length, "핵심 일부 구현, 연동/QA 남음"],
  ["개발중", featureRows.filter((r) => r.status === "개발중").length, "현재 구현 중"],
  ["스텁/API계약", stubCount, "컨트롤러/DTO는 있으나 TODO/고정값 중심"],
  ["미시작", featureRows.filter((r) => r.status === "미시작").length, "계약도 부족하거나 시작 전"],
  ["막힘", featureRows.filter((r) => r.status === "막힘").length, "외부 의존으로 진행 불가"],
  ["확인필요", featureRows.filter((r) => r.status === "확인필요").length, "정책/담당 확인 필요"],
];
styleHeader(summary.getRange("A11:C11"));
applyTableStyle(summary, "A11:C18");

const statusSheet = workbook.worksheets.add("백엔드 기능현황");
addTitle(statusSheet, "백엔드 기능/상태/담당 현황", "담당 번호는 1차 가정입니다. 팀원이 맞는지 확인 후 수정하세요.", "M");
const featureHeader = ["No", "담당번호", "담당/파트", "도메인", "기능", "주요 API", "상태", "진행률", "현재 구현 상태", "완료된 것", "남은 일", "막힘/의존성", "프론트 연동 메모", "근거 파일"];
statusSheet.getRange("A4:N4").values = [featureHeader];
styleHeader(statusSheet.getRange("A4:N4"));
statusSheet.getRange(`A5:N${featureRows.length + 4}`).values = featureRows.map((row) => [
  row.no,
  row.role,
  row.owner,
  row.domain,
  row.feature,
  row.api,
  row.status,
  row.percent / 100,
  row.backend,
  row.done,
  row.todo,
  row.blocker,
  row.feNeed,
  row.evidence,
]);
statusSheet.getRange(`H5:H${featureRows.length + 4}`).format.numberFormat = "0%";
applyTableStyle(statusSheet, `A4:N${featureRows.length + 4}`);
statusSheet.tables.add(`A4:N${featureRows.length + 4}`, true, "BackendStatusTable");
statusSheet.freezePanes.freezeRows(4);
statusSheet.getRange(`G5:G${featureRows.length + 4}`).dataValidation = { rule: { type: "list", values: statuses } };

const roleSheet = workbook.worksheets.add("역할번호");
addTitle(roleSheet, "역할 번호 / 담당 파트 확인표", "팀원 이름을 넣고 맞는지 확인받는 용도", "F");
roleSheet.getRange("A4:F4").values = [["담당번호", "파트", "관련 도메인", "현재 상태", "범위/메모", "실제 담당자 이름"]];
styleHeader(roleSheet.getRange("A4:F4"));
roleSheet.getRange(`A5:F${roleRows.length + 4}`).values = roleRows.map((r) => [...r, ""]);
applyTableStyle(roleSheet, `A4:F${roleRows.length + 4}`);
roleSheet.tables.add(`A4:F${roleRows.length + 4}`, true, "RoleMapTable");
roleSheet.freezePanes.freezeRows(4);

const schedule = workbook.worksheets.add("연동일정");
addTitle(schedule, "백엔드-프론트 연동 일정 초안", "날짜는 초안입니다. 프론트 PM과 API 확정일/개발 완료일을 따로 조정하세요.", "F");
schedule.getRange("A4:F4").values = [["날짜", "작업", "범위", "담당", "상태", "메모"]];
styleHeader(schedule.getRange("A4:F4"));
schedule.getRange(`A5:F${scheduleRows.length + 4}`).values = scheduleRows;
applyTableStyle(schedule, `A4:F${scheduleRows.length + 4}`);
schedule.tables.add(`A4:F${scheduleRows.length + 4}`, true, "IntegrationScheduleTable");
schedule.getRange(`E5:E${scheduleRows.length + 4}`).dataValidation = { rule: { type: "list", values: ["예정", "진행중", "완료", "지연", "보류"] } };

const qSheet = workbook.worksheets.add("확인질문");
addTitle(qSheet, "PM 확인 질문 / 막힘 목록", "팀 채팅에 그대로 복사해서 확인받을 수 있는 항목", "E");
qSheet.getRange("A4:E4").values = [["대상", "질문/이슈", "현재 판단", "상태", "다음 액션"]];
styleHeader(qSheet.getRange("A4:E4"));
qSheet.getRange(`A5:E${questions.length + 4}`).values = questions;
applyTableStyle(qSheet, `A4:E${questions.length + 4}`);
qSheet.tables.add(`A4:E${questions.length + 4}`, true, "OpenQuestionsTable");
qSheet.getRange(`D5:D${questions.length + 4}`).dataValidation = { rule: { type: "list", values: ["확인 완료", "확인 필요", "진행 예정", "공유 필요", "도입 제안"] } };

const daily = workbook.worksheets.add("일일보고양식");
addTitle(daily, "팀원 일일 공유 양식", "퍼센트는 참고용이고, 막힘/연동 변경/내일 할 일을 중심으로 받는 양식", "H");
daily.getRange("A4:H4").values = [["날짜", "담당번호", "담당자", "오늘 한 일", "진행률", "막힘/도움 필요", "내일 할 일", "API/DTO 변경"]];
styleHeader(daily.getRange("A4:H4"));
const blankRows = Array.from({ length: 15 }, () => [today, "", "", "", "", "", "", ""]);
daily.getRange("A5:H19").values = blankRows;
daily.getRange("E5:E19").format.numberFormat = "0%";
daily.getRange("B5:B19").dataValidation = { rule: { type: "list", values: roleRows.map((r) => r[0]) } };
applyTableStyle(daily, "A4:H19");
daily.tables.add("A4:H19", true, "DailyReportTable");
daily.freezePanes.freezeRows(4);

for (const sheet of [summary, statusSheet, roleSheet, schedule, qSheet, daily]) {
  sheet.showGridLines = false;
  sheet.getUsedRange().format.autofitColumns();
  sheet.getUsedRange().format.autofitRows();
}

// Keep wide text sheets readable without making columns absurdly wide.
statusSheet.getRange("E:E").format.columnWidth = 28;
statusSheet.getRange("F:F").format.columnWidth = 34;
statusSheet.getRange("I:N").format.columnWidth = 36;
roleSheet.getRange("E:E").format.columnWidth = 52;
schedule.getRange("B:B").format.columnWidth = 34;
schedule.getRange("F:F").format.columnWidth = 44;
qSheet.getRange("B:C").format.columnWidth = 48;
qSheet.getRange("C:C").format.columnWidth = 72;
qSheet.getRange("E:E").format.columnWidth = 34;
daily.getRange("D:H").format.columnWidth = 28;

const errorScan = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
});
console.log(errorScan.ndjson);

await fs.mkdir(outputDir, { recursive: true });
for (const sheetName of ["요약", "백엔드 기능현황", "역할번호", "연동일정", "확인질문", "일일보고양식"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${outputDir}/${sheetName}.png`, new Uint8Array(await preview.arrayBuffer()));
}

const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(`${outputDir}/backend_pm_status_${today}.xlsx`);
