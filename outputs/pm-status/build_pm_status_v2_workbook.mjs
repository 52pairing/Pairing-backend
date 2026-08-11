import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "C:/52_Pairing/Pairing-backend/outputs/pm-status";
const today = "2026-08-07";
const workbook = Workbook.create();

const owners = {
  p1: "1번 - 회원/인증 + 관리자 회원관리",
  p2: "2번 - 프로필/등급/리뷰 + 알림/챗봇",
  p3: "3번 - 프로젝트/계약/정산",
  p4: "4번 - AI 매칭",
  p5: "5번 - A2A 협상",
};

const backendRows = [
  ["회원가입 - 클라이언트/프리랜서/소셜 프리랜서", owners.p1, "완료", "완료", "2026-08-07", "", "약관/메타 코드, 이메일 인증", "FE 연동 가능"],
  ["로그인/로그아웃/JWT 재발급/내 정보", owners.p1, "완료", "완료", "2026-08-07", "", "Redis 세션/토큰 저장소", "FE 연동 가능"],
  ["이메일 인증/발송 제한/코드 확인", owners.p1, "완료", "완료", "2026-08-07", "운영 SMTP 계정 검증 필요", "SMTP 환경변수", "FE 연동 가능"],
  ["비밀번호 찾기/초기화/변경/계정 잠금 해제", owners.p1, "완료", "완료", "2026-08-07", "", "이메일 인증, Redis 토큰", "FE 연동 가능"],
  ["소셜 로그인 - Kakao/Google", owners.p1, "완료", "완료", "2026-08-07", "실제 OAuth 키/redirect URI 운영 검증 필요", "프론트 콜백 URL", "FE 연동 가능"],
  ["권한/Security/CurrentAccountId/전역 예외", owners.p1, "완료", "완료", "2026-08-07", "", "전체 도메인 권한 정책", "전체 API 공통"],
  ["탈퇴 제한/계정 삭제", owners.p1, "API계약/TODO", "2026-08-10 담당 확인", "", "진행 중 프로젝트/협상/정산 상태 필요", "3번 프로젝트/정산, 5번 협상 상태", "FE 대기"],
  ["관리자 회원 조회/상세/정지/해제", owners.p1, "API계약/TODO", "2026-08-10 담당 확인", "", "집계/검색 쿼리 미구현", "회원 상태 데이터", "FE 대기"],

  ["프리랜서 마이페이지/프로필 수정", owners.p2, "API계약/TODO", "2026-08-10 담당 확인", "", "실제 profile 조회/수정 서비스 미구현", "1번 계정 정보, 파일 업로드", "FE 더미 가능"],
  ["프리랜서 조건 등록/수정", owners.p2, "API계약/TODO", "2026-08-10 담당 확인", "", "freelancer_condition 저장 미구현", "메타 코드, 4번 임베딩 호출", "FE 더미 가능"],
  ["프리랜서 이력서/경력/학력/자격/링크", owners.p2, "API계약/TODO", "2026-08-10 담당 확인", "", "resume 및 하위 목록 저장 미구현", "파일 업로드, 4번 프로필 임베딩", "FE 더미 가능"],
  ["포트폴리오 CRUD", owners.p2, "API계약/TODO", "2026-08-10 담당 확인", "", "파일 소유 확인/저장 미구현", "3번/공통 파일 업로드", "FE 더미 가능"],
  ["클라이언트 기업정보/마이페이지", owners.p2, "API계약/TODO", "2026-08-10 담당 확인", "", "기업 프로필 조회/수정/리뷰 집계 미구현", "1번 계정, 파일 업로드", "FE 더미 가능"],
  ["등급 계산/등급 조회", owners.p2, "API계약/TODO", "2026-08-11 담당 확인", "", "완료 건수/평점 집계 필요", "3번 계약/정산, 2번 리뷰", "FE 더미 가능"],
  ["리뷰 작성/받은 리뷰/작성 리뷰/요약", owners.p2, "API계약/TODO", "2026-08-11 담당 확인", "", "계약 종료/대금 지급 확인 필요", "3번 계약/정산 완료 데이터", "FE 더미 가능"],
  ["사이트 리뷰 관리자 공개 설정", owners.p2, "API계약/TODO", "2026-08-11 담당 확인", "", "리뷰 검색/집계 미구현", "리뷰 저장 데이터", "FE 더미 가능"],
  ["알림 목록/읽음/삭제/안읽음 수", owners.p2, "API계약/TODO", "2026-08-11 담당 확인", "", "알림 생성 이벤트 미구현", "각 도메인 이벤트", "FE 더미 가능"],
  ["FAQ/홈 요약/사이트 후기", owners.p2, "API계약/TODO", "2026-08-11 담당 확인", "", "집계/FAQ 데이터 소스 미구현", "리뷰/프로젝트/협상 통계", "FE 더미 가능"],
  ["챗봇 질문/쿼터/세션 메시지", owners.p2, "API계약/TODO", "2026-08-12 담당 확인", "", "AI 서버 호출/쿼터 저장 미구현", "AI 챗봇 모델/FAQ 데이터", "FE 대기"],

  ["파일 업로드/조회/삭제", owners.p3, "API계약/TODO", "2026-08-10 담당 확인", "", "S3 저장/file 테이블/권한 미구현", "S3 환경변수, 소유 도메인 정보", "FE 대기"],
  ["프로젝트 등록/수정/취소/상세", owners.p3, "API계약/TODO", "2026-08-10 담당 확인", "", "프로젝트+포지션+스킬 저장 미구현", "2번 클라이언트, 파일, 메타 코드", "FE 더미 가능"],
  ["프로젝트 사전 검수/pre-review", owners.p3, "API계약/TODO", "2026-08-10 담당 확인", "", "후보 집계/조건 완화 제안 미구현", "2번 프리랜서 조건, 4번 임베딩 트리거", "FE 더미 가능"],
  ["내 프로젝트 목록/탭 카운트/관리자 프로젝트", owners.p3, "API계약/TODO", "2026-08-11 담당 확인", "", "상태별 조회/집계 미구현", "매칭/협상/계약 상태", "FE 더미 가능"],
  ["모집 마감/모집 연장/상태머신", owners.p3, "API계약/TODO", "2026-08-11 담당 확인", "", "상태 전이/만료 요청 처리 미구현", "4번 매칭 요청 상태", "FE 대기"],
  ["계약 목록/상세/PDF/서명/거절", owners.p3, "API계약/TODO", "2026-08-12 담당 확인", "", "협상 결과 기반 계약 생성/PDF 미구현", "5번 협상 완료 데이터, 파일", "FE 더미 가능"],
  ["계약 완료/중도 종료/검수", owners.p3, "API계약/TODO", "2026-08-12 담당 확인", "", "상태 전이와 정산/리뷰 대상 생성 미구현", "정산, 리뷰", "FE 대기"],
  ["정산/착수금/성공보수/위약금", owners.p3, "API계약/TODO", "2026-08-12 담당 확인", "", "원장/결제/수수료 계산 미구현", "계약 상태, 결제수단", "FE 더미 가능"],
  ["관리자 정산 조회/요약", owners.p3, "API계약/TODO", "2026-08-12 담당 확인", "", "정산 집계 쿼리 미구현", "정산/원장 데이터", "FE 더미 가능"],

  ["AI 서버 연동 설정/RestClient/서킷브레이커", owners.p4, "부분완료", "2026-08-10", "", "실제 AI 서버 환경 연결 QA 필요", "Pairing-python 실행 환경", "BE 내부 연동"],
  ["임베딩 1차 후보 추림 호출", owners.p4, "부분완료", "2026-08-10", "", "Python 하드필터/프롬프트 개선 남음", "2번 프로필 텍스트, 3번 프로젝트 텍스트", "BE 내부 연동"],
  ["LLM 최종 후보 선정/랭킹/근거", owners.p4, "부분완료", "2026-08-10", "", "Pairing-python _build_prompt 미완성", "2번 이력서/3번 포지션 데이터", "FE는 후보 API로 연동"],
  ["후보 노출/후보 카드/lowScoreWarned", owners.p4, "부분완료", "2026-08-10", "", "후보 이름/사진/등급/평점은 스텁", "2번 FreelancerDirectoryPort 실제 구현", "FE 연동 가능(일부 스텁)"],
  ["후보 거절/재추천/리롤 제한", owners.p4, "완료에 가까움", "2026-08-10", "", "유료 결제 플로우 연결 필요", "3번 정산/결제", "FE 연동 가능"],
  ["매칭 요청 발송/보낸 요청/받은 요청/상세", owners.p4, "완료에 가까움", "2026-08-10", "", "프로젝트/프리랜서 요약은 스텁", "2번/3번 실제 데이터 포트", "FE 연동 가능(일부 스텁)"],
  ["매칭 요청 수락/거절", owners.p4, "부분완료", "2026-08-10", "", "수락 시 협상 생성은 StubNegotiationAdapter", "5번 NegotiationCommandUseCase", "FE 연동 가능(협상 진입 대기)"],
  ["Stage F 직무/스킬/예산 가드", owners.p4, "미구현", "2026-08-11", "", "현재 applyGuard(true) placeholder", "2번 조건/3번 예산/정산 정책", "BE 내부"],
  ["프로필/프로젝트 임베딩 저장 트리거", owners.p4, "미구현", "2026-08-11", "", "저장 직후 PUT embeddings 호출 배선 필요", "2번/3번 저장 UseCase 위치", "BE 내부"],

  ["협상 개시/협상방 생성", owners.p5, "API계약/TODO", "2026-08-11 담당 확인", "", "application/usecase 부재", "4번 매칭 수락 이벤트/스냅샷", "FE 대기"],
  ["조건별 협상/금액 협상/마지노선", owners.p5, "API계약/TODO", "2026-08-12 담당 확인", "", "협상 조건 저장/라운드 처리 미구현", "4번 budgetCap/floor/snapshot", "FE 더미 가능"],
  ["AI 제안/이유/응답/최종 승인", owners.p5, "API계약/TODO", "2026-08-12 담당 확인", "", "협상 AI 호출/로그 저장 미구현", "AI 서버/ai_agent_log", "FE 대기"],
  ["협상 포기/AI Out/협상 결렬", owners.p5, "API계약/TODO", "2026-08-12 담당 확인", "", "매칭 상태 NEGOTIATION_FAILED 연동 필요", "4번 매칭 상태 변경 포트", "FE 대기"],
  ["협상 로그/관리자 협상 조회", owners.p5, "API계약/TODO", "2026-08-12 담당 확인", "", "로그/집계 쿼리 미구현", "ai_agent_log, negotiation_message", "FE 더미 가능"],
  ["협상채팅/채팅방/메시지/읽음/나가기", owners.p5, "API계약/TODO", "2026-08-13 담당 확인", "", "chat 저장/STOMP/read 처리 미구현", "협상 성사/AI Out 상태", "FE 대기"],
];

const frontendRows = [
  ["회원가입/로그인/소셜로그인", owners.p1, "연동 가능", "2026-08-09 FE 확인", "", "쿠키/401/role 처리 확인", "1번 Auth API", "BE 완료"],
  ["아이디/비밀번호 찾기/이메일 인증", owners.p1, "연동 가능", "2026-08-09 FE 확인", "", "타이머/재발송 UI", "1번 이메일 인증 API", "BE 완료"],
  ["프리랜서 조건/이력서/포트폴리오 화면", owners.p2, "더미 가능", "2026-08-11 FE 확인", "", "실제 저장 API 미완성", "2번 저장 API, 4번 임베딩 트리거", "BE 대기"],
  ["클라이언트 마이페이지/기업정보", owners.p2, "더미 가능", "2026-08-11 FE 확인", "", "실제 저장 API 미완성", "2번 클라이언트 API", "BE 대기"],
  ["리뷰/등급/알림/챗봇 화면", owners.p2, "더미 가능", "2026-08-12 FE 확인", "", "집계/알림 이벤트/챗봇 미완성", "2번 리뷰/알림/챗봇 API", "BE 대기"],
  ["프로젝트 등록/검수/목록/상세", owners.p3, "더미 가능", "2026-08-11 FE 확인", "", "실제 저장/상태 API 미완성", "3번 Project API", "BE 대기"],
  ["계약/PDF/서명 화면", owners.p3, "더미 가능", "2026-08-13 FE 확인", "", "협상 완료 데이터와 PDF 미완성", "3번 계약, 5번 협상", "BE 대기"],
  ["정산/결제/위약금 화면", owners.p3, "더미 가능", "2026-08-13 FE 확인", "", "결제/원장 미완성", "3번 정산, 1번/3번 결제수단", "BE 대기"],
  ["AI 추천 후보/재추천/매칭 요청 화면", owners.p4, "부분 연동 가능", "2026-08-10 FE 확인", "", "후보 카드 일부 스텁. fitScore 배지 제거 필요", "2번 후보 카드 데이터", "BE 부분완료"],
  ["받은 프로젝트 제안/수락/거절 화면", owners.p4, "부분 연동 가능", "2026-08-10 FE 확인", "", "수락 후 협상 진입은 5번 대기", "5번 협상 생성", "BE 부분완료"],
  ["협상방/조건 입력/AI 제안/로그 화면", owners.p5, "더미 가능", "2026-08-13 FE 확인", "", "협상 실제 로직 미구현", "5번 협상 API", "BE 대기"],
  ["협상채팅 화면", owners.p5, "더미 가능", "2026-08-13 FE 확인", "", "STOMP/메시지 저장 미구현", "5번 협상채팅 API", "BE 대기"],
  ["관리자 회원/프로젝트/협상/정산/리뷰", "1번/2번/3번/5번", "더미 가능", "2026-08-14 FE 확인", "", "대부분 집계 API 미구현", "각 도메인 admin API", "BE 대기"],
];

const integrationRows = [
  ["2번 → 4번", "프리랜서 후보 카드 데이터", "이름/사진/등급/평점/조건/스킬", "필요", "2026-08-09 담당 확인", "", "4번 후보 조회가 현재 스텁값 사용"],
  ["2번 → 4번", "프로필 임베딩 저장 트리거", "이력서/조건 저장 후 PUT /embeddings/freelancers 호출 위치", "필요", "2026-08-10", "", "4번 어댑터 또는 별도 AI 포트 배선"],
  ["3번 → 4번", "프로젝트/포지션 요약", "projectId/positionId/headcount/budgetAmount/skills/소유권", "필요", "2026-08-09 담당 확인", "", "ProjectDirectoryPort 교체 필요"],
  ["3번 → 4번", "포지션 임베딩 저장 트리거", "검수 통과 시 PUT /embeddings/positions 호출", "필요", "2026-08-10", "", "프로젝트 등록 플로우에 연결"],
  ["3번 → 4번", "최초 매칭 라운드 시작", "착수금 결제 완료 후 MatchingRoundCreationService 호출", "필요", "2026-08-11", "", "정산/결제 이벤트 필요"],
  ["4번 → 5번", "매칭 수락 시 협상 생성", "projectId/positionId/requestId/freelancerId/budgetCap/snapshot", "필요", "2026-08-10", "", "NegotiationCommandUseCase 필요"],
  ["4번 ↔ 5번", "협상 스냅샷 필드", "payUnit/payAmount/workStyle/workForm/availableFrom/startNegotiable/minAcceptAmount/period", "확인중", "2026-08-08", "", "minAcceptAmount는 월단가(원) 기준으로 답변"],
  ["5번 → 3번", "협상 완료 후 계약 생성", "타결 금액/기간/근무조건/특약/로그", "필요", "2026-08-12", "", "계약 자동 생성"],
  ["3번 → 2번", "계약/정산 완료 후 리뷰/등급", "완료 계약, 평점, 수수료/성공보수", "필요", "2026-08-13", "", "리뷰 작성 가능 조건"],
  ["전체 → 2번", "알림 이벤트", "매칭/협상/계약/정산/문의 이벤트", "필요", "2026-08-13", "", "알림 생성 공통 포트 필요"],
  ["BE ↔ FE", "점수 노출 정책", "fitScore 미노출, lowScoreWarned 배너만 사용", "공유필요", "2026-08-08", "", "Figma AI 점수 배지 제거"],
  ["BE ↔ FE", "일일 연동 상태 공유", "API 확정일/완료일/DTO 변경/막힘", "필요", "매일", "", "프론트 PM과 10분 싱크"],
];

const ownerRows = [
  ["1번", "회원/인증 + 관리자 회원관리", "가입, 로그인, JWT, Redis, 이메일 인증, 비번찾기, 소셜로그인, 권한, 탈퇴 제한, 관리자 회원 조회/정지", "없음"],
  ["2번", "프로필/등급/리뷰 + 알림/챗봇", "프리랜서 프로필, 이력서/포트폴리오, 클라이언트 기업정보, 마이페이지, 등급 계산, 리뷰/평점, 알림 조회/읽음/삭제, FAQ 챗봇", "프로필 임베딩 저장, FAQ 챗봇"],
  ["3번", "프로젝트/계약/정산", "프로젝트 등록/수정, 상태머신, 모집기간/연장, 자료 업로드, 진행관리, 검수, 표준계약서 PDF, 수수료 정산", "프로젝트"],
  ["4번", "AI 매칭", "임베딩 1차 후보 추림, LLM 최종 후보 선정, 후보 노출, 후보 선택, 매칭 요청, 수락/거절, 재선택, 리롤", "매칭 AI, 임베딩 저장"],
  ["5번", "A2A 협상", "협상 개시, 협상방 생성, 조건별 협상, 금액 협상, AI 제안/이유, 금액 가드, 협상 포기, AI Out, 협상 로그, 협상채팅", "협상 AI"],
];

function title(sheet, text, sub, endCol) {
  sheet.getRange(`A1:${endCol}1`).merge();
  sheet.getRange("A1").values = [[text]];
  sheet.getRange(`A2:${endCol}2`).merge();
  sheet.getRange("A2").values = [[sub]];
  sheet.getRange(`A1:${endCol}1`).format = { fill: "#12343B", font: { bold: true, color: "#FFFFFF", size: 16 } };
  sheet.getRange(`A2:${endCol}2`).format = { fill: "#E8F3F1", font: { italic: true, color: "#12343B" } };
}

function header(range) {
  range.format = { fill: "#2D5A63", font: { bold: true, color: "#FFFFFF" }, wrapText: true };
}

function style(sheet, range) {
  sheet.getRange(range).format.borders = { preset: "all", style: "thin", color: "#D9E2E1" };
  sheet.getRange(range).format.wrapText = true;
}

function addStatusSheet(name, rows) {
  const sheet = workbook.worksheets.add(name);
  title(sheet, `${name} 상태표`, `기준일 ${today}. 담당/예정일은 PM 초안이며 담당자 확인 후 수정`, "H");
  sheet.getRange("A4:H4").values = [["기능", "담당", "상태", "완료예정일", "완료일", "막힌점", "필요한거(다른 팀원 파트)", "연동여부"]];
  header(sheet.getRange("A4:H4"));
  sheet.getRange(`A5:H${rows.length + 4}`).values = rows;
  style(sheet, `A4:H${rows.length + 4}`);
  sheet.tables.add(`A4:H${rows.length + 4}`, true, `${name.replaceAll(" ", "")}Table`);
  sheet.freezePanes.freezeRows(4);
  sheet.getRange(`C5:C${rows.length + 4}`).dataValidation = {
    rule: { type: "list", values: ["완료", "완료에 가까움", "부분완료", "API계약/TODO", "더미 가능", "연동 가능", "부분 연동 가능", "미구현", "필요", "확인중", "공유필요"] },
  };
  return sheet;
}

const summary = workbook.worksheets.add("요약");
title(summary, "Pairing PM 진행 현황", "요청 컬럼 기준: 기능/담당/상태/예정일/완료일/막힘/필요한 것/연동여부", "H");
summary.getRange("A4:D4").values = [["담당", "파트", "포함 기능", "AI 역할"]];
header(summary.getRange("A4:D4"));
summary.getRange(`A5:D${ownerRows.length + 4}`).values = ownerRows;
style(summary, `A4:D${ownerRows.length + 4}`);
summary.tables.add(`A4:D${ownerRows.length + 4}`, true, "OwnerMapTable");
summary.getRange("A12:H12").values = [["구분", "총 기능", "완료/완료에 가까움", "부분완료", "API계약/TODO", "미구현/대기", "핵심 막힘", "PM 다음 액션"]];
header(summary.getRange("A12:H12"));
const countBy = (rows, pred) => rows.filter(pred).length;
summary.getRange("A13:H16").values = [
  ["백엔드", backendRows.length, countBy(backendRows, (r) => ["완료", "완료에 가까움"].includes(r[2])), countBy(backendRows, (r) => r[2] === "부분완료"), countBy(backendRows, (r) => r[2].includes("API계약")), countBy(backendRows, (r) => r[2] === "미구현"), "2번/3번/5번 실제 구현 전까지 4번 매칭 일부 스텁", "담당자별 완료예정일 확정"],
  ["프론트엔드", frontendRows.length, countBy(frontendRows, (r) => r[2].includes("연동 가능")), countBy(frontendRows, (r) => r[2].includes("부분")), countBy(frontendRows, (r) => r[2].includes("더미")), 0, "BE 실제 구현 전 더미 연동 구간 많음", "FE PM과 DTO 확정일/연동일 분리"],
  ["연동", integrationRows.length, 0, 0, 0, countBy(integrationRows, (r) => ["필요", "확인중", "공유필요"].includes(r[3])), "임베딩/협상생성/계약생성/알림 이벤트", "매일 10분 싱크"],
  ["오늘 공유할 말", "", "", "", "", "", "퍼센트보다 막힌점과 필요한 파트를 매일 업데이트", "이 파일을 기준으로 각자 칸 수정 요청"],
];
style(summary, "A12:H16");

const backend = addStatusSheet("백엔드", backendRows);
const frontend = addStatusSheet("프론트엔드", frontendRows);
const integration = workbook.worksheets.add("연동현황표");
title(integration, "연동현황표", "팀 간 의존성/필요 데이터/연동 상태 확인용", "H");
integration.getRange("A4:H4").values = [["기능", "담당", "상태", "완료예정일", "완료일", "막힌점", "필요한거(다른 팀원 파트)", "연동여부"]];
header(integration.getRange("A4:H4"));
integration.getRange(`A5:H${integrationRows.length + 4}`).values = integrationRows.map((r) => [
  r[1],
  r[0],
  r[3],
  r[4],
  r[5],
  r[6],
  r[2],
  r[7],
]);
style(integration, `A4:H${integrationRows.length + 4}`);
integration.tables.add(`A4:H${integrationRows.length + 4}`, true, "IntegrationStatusTable");
integration.freezePanes.freezeRows(4);

for (const sheet of [summary, backend, frontend, integration]) {
  sheet.showGridLines = false;
  sheet.getUsedRange().format.autofitColumns();
  sheet.getUsedRange().format.autofitRows();
  sheet.getRange("A:A").format.columnWidth = 36;
  sheet.getRange("B:B").format.columnWidth = 34;
  sheet.getRange("C:C").format.columnWidth = 18;
  sheet.getRange("D:E").format.columnWidth = 18;
  sheet.getRange("F:H").format.columnWidth = 38;
}
summary.getRange("C:C").format.columnWidth = 70;
summary.getRange("D:D").format.columnWidth = 26;

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
});
console.log(errors.ndjson);

await fs.mkdir(outputDir, { recursive: true });
for (const sheetName of ["요약", "백엔드", "프론트엔드", "연동현황표"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${outputDir}/${sheetName}_v2.png`, new Uint8Array(await preview.arrayBuffer()));
}
const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(`${outputDir}/pairing_pm_status_${today}_v2.xlsx`);
