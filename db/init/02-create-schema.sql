-- =====================================================================
-- pairing 스키마 생성 (테이블/제약/인덱스/주석/트리거)  [PostgreSQL]
--
-- 선행: db/init/01-create-pairing-account.sql 로 pairing 역할·데이터베이스를 먼저 만든다.
--
-- 실행 (psql):
--   psql -U pairing -d pairing -v ON_ERROR_STOP=1 -f db/init/02-create-schema.sql
--
-- 실행 (pgAdmin):
--   pairing 데이터베이스에 접속한 뒤 이 파일 전체를 실행한다.
--
-- 주의: 이 스크립트는 DROP 을 하지 않는다. 이미 테이블이 있으면 오류가 난다.
--       다시 만들려면 데이터베이스를 비우고 실행한다.
--         DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO pairing;
--
-- application.yaml 의 spring.jpa.hibernate.ddl-auto 는 validate 로 두었다.
-- 엔티티와 테이블이 어긋나면 애플리케이션이 시작에 실패한다. 스키마 변경은 이 파일로만 한다.
--
-- [v12 이후 수정 - 2026-08-05: 계정 유니크를 역할별로 분리]
--   한 사람이 클라이언트와 프리랜서로 각각 가입할 수 있다. 이메일/휴대폰을 전역 유니크로 두면
--   같은 사람이 두 역할을 가질 수 없으므로 role 을 포함한 복합 유니크로 바꾼다.
--    - uk_account_email -> uk_account_email_role (email, role)
--    - uk_account_phone -> uk_account_phone_role (phone, role)
--   같은 역할 안에서는 여전히 소셜↔일반 이메일 중복도 불가하다.
--
-- [v12 이후 수정 - 2026-08-05: 챗봇/1:1 문의 분리]
--   1:1 문의와 챗봇 질의는 서로 독립된 창구이며 사용자가 선택한다.
--   챗봇 미해결을 전제로 한 에스컬레이션 구조를 걷어냈다.
--    - inquiry.session_id 삭제 (fk_inquiry_session, idx_inquiry_session 포함)
--    - inquiry.status: RECEIVED/IN_PROGRESS/ANSWERED/CLOSED -> PENDING/ANSWERED (요구사항 R46 기준), DEFAULT 'PENDING'
--    - chatbot_session.status: ESCALATED 제거 -> ACTIVE/CLOSED
-- =====================================================================

-- =====================================================================
-- AI 프리랜서 매칭 플랫폼 (페어링) - PostgreSQL 스키마 v12
-- ERD 클라우드: 새 ERD > 가져오기(Import) > PostgreSQL > 아래 전체 붙여넣기
--
-- [설계 원칙]
--  1. PK: BIGINT GENERATED ALWAYS AS IDENTITY
--  2. 코드성 값은 VARCHAR + 주석 (Java enum 과 @Enumerated(STRING) 으로 매핑)
--  3. 금액 NUMERIC(15,0) 원 단위 / 비율 NUMERIC(5,2) 퍼센트
--  4. 시각 TIMESTAMP(UTC 저장) / 애플리케이션에서 KST 변환
--  5. 비정형 데이터는 JSONB
--
-- [v12 변경 - MySQL -> PostgreSQL 전환 및 범위 정리]
--  - 타입: TINYINT(1)->BOOLEAN, DATETIME->TIMESTAMP, DECIMAL->NUMERIC,
--          JSON->JSONB, VARBINARY->BYTEA, AUTO_INCREMENT->IDENTITY
--  - 인덱스/유니크/FK 를 CREATE TABLE 밖으로 분리 (PostgreSQL 표준)
--  - 컬럼 설명은 COMMENT ON 구문으로 분리
--  - updated_at 자동 갱신은 트리거로 처리 (MySQL 의 ON UPDATE 대체)
--
--  [삭제된 테이블]
--   freelancer_embedding / position_embedding -> Pairing-python(FastAPI) 소유, pgvector로 같은 DB에 저장(별도 스키마 파일)
--   project_parse  -> AI 검수 결과는 FastAPI 측에서 처리
--   deliverable    -> 산출물 제출·검수 기능 제외
--
--  [삭제된 컬럼]
--   account.suspended_at   -> 정지 상태는 Redis 에서 관리
--   account.purged_at
--   terms_agreement.ip_address
--   resume_career.sort_order
--   resume_link.link_type  -> URL 만 저장
--
--  [기타]
--   email_verification.purpose 에서 PASSWORD_RESET 제거
--     비밀번호 초기화는 링크 접속 시 임시 비밀번호 발급 방식이며 토큰은 Redis
--   reroll -> rerecommend 로 용어 통일
--   chat_message 에 message_type / payload(JSONB) 추가
--
-- [Redis 로 관리하는 것]
--   JWT Refresh Token (Access 30분 / Refresh 7일)
--   회원 정지 상태
--   비밀번호 초기화 링크 토큰
--   이메일 발송 제한(1시간 15회) / 로그인 IP 차단(1시간 20회 -> 2시간)
--
-- [application.yml 상수]
--   rerecommend: free-max=1, paid-max=5, unit-price=10000
--   recruit: weeks=2, extension-max=2
--   negotiation: round-max=15
--   matching: request-expire-days=3
--   chatbot: daily-quota=10
--   fee: 1억 미만 착수금 클라 3%/프리 4%, 성공보수 클라 7%/프리 6%
--        1억 이상 착수금 클라 2%/프리 4%, 성공보수 클라 6%/프리 6%
--   grade: 골드 3.0·10건 / 다이아 4.0·20건 / 시니어 3.0·5건 / 마스터 4.0·10건
--
-- [탈퇴 처리 규칙]
--   1. account 행은 물리 삭제하지 않는다. review / contract / settlement FK 가 있다.
--   2. 탈퇴 시 email 을 withdrawn_{id}@pairing.local 로 치환하고
--      원본은 email_hash 에만 남긴다(30일 재가입 제한 판정).
--   3. purge_at 도래 시 개인정보 컬럼만 마스킹한다. DELETE 하지 않는다.
-- =====================================================================


-- =====================================================================
-- 1. 테이블
-- =====================================================================

CREATE TABLE "file" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "file_group" VARCHAR(30) NOT NULL,
    "original_name" VARCHAR(255) NOT NULL,
    "storage_key" VARCHAR(500) NOT NULL,
    "mime_type" VARCHAR(100) NOT NULL,
    "size_bytes" BIGINT NOT NULL,
    "uploaded_by" BIGINT,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "terms" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "code" VARCHAR(50) NOT NULL,
    -- AGREEMENT: 가입 시 동의를 받는 약관 / POLICY: 게시만 하는 문서(개인정보 처리방침)
    -- 개인정보 처리방침은 보호법 제30조상 '공개' 대상이지 동의 대상이 아니라 구분해 둔다.
    "terms_type" VARCHAR(20) DEFAULT 'AGREEMENT' NOT NULL,
    "version" VARCHAR(20) NOT NULL,
    "title" VARCHAR(200) NOT NULL,
    "content" TEXT NOT NULL,
    "is_required" BOOLEAN DEFAULT TRUE NOT NULL,
    "target_role" VARCHAR(20),
    "effective_at" TIMESTAMP NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "account" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "email" VARCHAR(255) NOT NULL,
    "password_hash" VARCHAR(60),
    "role" VARCHAR(20) NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    "phone" VARCHAR(20) NOT NULL,
    "signup_type" VARCHAR(20) NOT NULL,
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "email_verified" BOOLEAN DEFAULT FALSE NOT NULL,
    "login_fail_count" INTEGER DEFAULT 0 NOT NULL,
    "locked_at" TIMESTAMP,
    "is_temp_password" BOOLEAN DEFAULT FALSE NOT NULL,
    "password_updated_at" TIMESTAMP,
    "last_login_at" TIMESTAMP,
    "suspended_at" TIMESTAMP,
    "suspend_reason" VARCHAR(500),
    "withdrawn_at" TIMESTAMP,
    "withdraw_reason" VARCHAR(500),
    "email_hash" VARCHAR(64),
    "phone_hash" VARCHAR(64),
    "rejoin_available_at" TIMESTAMP,
    "purge_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "social_account" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "provider" VARCHAR(20) NOT NULL,
    "provider_uid" VARCHAR(255) NOT NULL,
    "provider_email" VARCHAR(255),
    "email_verified" BOOLEAN DEFAULT FALSE NOT NULL,
    "connected_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "client_profile" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "company_name" VARCHAR(100) NOT NULL,
    "business_no" CHAR(10) NOT NULL,
    "business_field" VARCHAR(40) NOT NULL,
    "employee_count" VARCHAR(30) NOT NULL,
    "address" VARCHAR(255),
    "logo_file_id" BIGINT,
    "grade" VARCHAR(20) DEFAULT 'SILVER' NOT NULL,
    "grade_checked_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "freelancer_profile" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "birth_date" DATE NOT NULL,
    "address" VARCHAR(255),
    "profile_file_id" BIGINT,
    "ai_matching_agreed" BOOLEAN DEFAULT TRUE NOT NULL,
    "matching_paused" BOOLEAN DEFAULT FALSE NOT NULL,
    "grade" VARCHAR(20) DEFAULT 'JUNIOR' NOT NULL,
    "grade_checked_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "payment_method" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "method_type" VARCHAR(20) NOT NULL,
    "is_default" BOOLEAN DEFAULT FALSE NOT NULL,
    "card_number_enc" BYTEA,
    "card_brand" VARCHAR(30),
    "card_last4" CHAR(4),
    "card_expiry_month" SMALLINT,
    "card_expiry_year" SMALLINT,
    "card_holder" VARCHAR(50),
    "easy_pay_provider" VARCHAR(20),
    "bank_code" VARCHAR(10),
    "account_no_enc" BYTEA,
    -- 계좌번호는 암호문만 저장하므로, 화면에 "**** 6789" 를 그리려면 끝 4자리를 따로 남겨야 한다.
    -- (카드의 card_last4 와 같은 이유)
    "account_last4" CHAR(4),
    "account_holder" VARCHAR(50),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "terms_agreement" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "terms_id" BIGINT NOT NULL,
    "agreed" BOOLEAN NOT NULL,
    "agreed_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "user_agent" VARCHAR(500),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "email_verification" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "email" VARCHAR(255) NOT NULL,
    "purpose" VARCHAR(30) NOT NULL,
    "code_hash" VARCHAR(255) NOT NULL,
    "expires_at" TIMESTAMP NOT NULL,
    "verified_at" TIMESTAMP,
    "attempt_count" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "freelancer_condition" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "job_category" VARCHAR(30) NOT NULL,
    "job_role" VARCHAR(40) NOT NULL,
    "affiliation" VARCHAR(100),
    "work_style" VARCHAR(20) NOT NULL,
    "work_form" VARCHAR(20) NOT NULL,
    "pay_unit" VARCHAR(20) NOT NULL,
    "pay_amount" NUMERIC(15,0) NOT NULL,
    "min_accept_amount" NUMERIC(15,0),
    "available_from" DATE,
    "start_negotiable" BOOLEAN DEFAULT FALSE NOT NULL,
    "period_value" INTEGER NOT NULL,
    "period_unit" VARCHAR(10) NOT NULL,
    "has_freelance_exp" BOOLEAN NOT NULL,
    "career_years" INTEGER NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "condition_skill" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "condition_id" BIGINT NOT NULL,
    "skill_code" VARCHAR(50) NOT NULL,
    "skill_level" VARCHAR(20) NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "resume" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    -- freelancer_profile.id 가 아니라 account.id 다. 이력서는 계정당 한 장이다.
    "account_id" BIGINT NOT NULL,
    "profile_file_id" BIGINT NOT NULL,
    "contact_phone" VARCHAR(20),
    "contact_email" VARCHAR(100),
    -- 주소는 세 칸으로 나눠 받는다(우편번호 검색 결과 + 사용자가 직접 쓰는 상세주소).
    -- 합쳐 저장하면 수정 화면에서 다시 나눌 수 없다. zip_code 는 옛 이력서에 없어 nullable.
    "zip_code" VARCHAR(10),
    "address" VARCHAR(255) NOT NULL,
    "address_detail" VARCHAR(255),
    "self_introduction" VARCHAR(2000) NOT NULL,
    "portfolio_file_id" BIGINT NOT NULL,
    "status" VARCHAR(10) DEFAULT 'DRAFT' NOT NULL,
    -- 이력서 등록 시 받는 필수 약관 4종. 하나라도 false 면 저장하지 않는다.
    "profile_collection_agreed" BOOLEAN DEFAULT FALSE NOT NULL,
    "profile_provision_agreed" BOOLEAN DEFAULT FALSE NOT NULL,
    "ai_analysis_agreed" BOOLEAN DEFAULT FALSE NOT NULL,
    "career_portfolio_usage_agreed" BOOLEAN DEFAULT FALSE NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 이력서 작성 중 임시 저장(초안). 화면 입력값을 검증 없이 통째로 보관한다.
--
-- resume 본체에 반쯤 채운 값을 넣지 않는 이유: resume 와 자식 테이블(resume_education 등)의
-- NOT NULL 을 전부 풀어야 하고, 그러면 "완성된 이력서만 매칭에 쓴다"는 보장이 깨진다.
-- 초안은 조회·집계 대상이 아니라 화면 복원용이라 관계형으로 쪼갤 이유도 없다.
CREATE TABLE "resume_draft" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    -- 위저드 1·2단계(희망 조건 + 이력서) 입력값을 한 덩어리 JSON 문자열로 담는다.
    "payload" TEXT NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "resume_education" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "resume_id" BIGINT NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE,
    "school_name" VARCHAR(100) NOT NULL,
    "major" VARCHAR(100),
    "graduation_status" VARCHAR(20) NOT NULL,
    "campus_type" VARCHAR(20),
    "sort_order" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "resume_career" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "resume_id" BIGINT NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE,
    "company_name" VARCHAR(100) NOT NULL,
    "department" VARCHAR(100),
    "position" VARCHAR(100),
    "job_description" TEXT,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "resume_certificate" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "resume_id" BIGINT NOT NULL,
    "acquired_date" DATE NOT NULL,
    "name" VARCHAR(100) NOT NULL,
    -- 발급기관과 점수는 화면 입력칸이 따로다. 합쳐 저장하면 수정 화면에서 다시 나눌 수 없다.
    "issuer" VARCHAR(100),
    "score" VARCHAR(50),
    "note" VARCHAR(255),
    "sort_order" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "resume_link" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "resume_id" BIGINT NOT NULL,
    "url" VARCHAR(500) NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 마이페이지 포트폴리오. 이력서 첨부 파일과 별개로 여러 건을 관리한다.
CREATE TABLE "portfolio" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "title" VARCHAR(100) NOT NULL,
    "description" VARCHAR(1000),
    "file_id" BIGINT,
    "link_url" VARCHAR(500),
    "sort_order" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "project" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "client_id" BIGINT NOT NULL,
    "title" VARCHAR(200) NOT NULL,
    "start_desired_date" DATE,
    "start_negotiable" BOOLEAN DEFAULT FALSE NOT NULL,
    "period_value" INTEGER NOT NULL,
    "period_unit" VARCHAR(10) NOT NULL,
    "budget_amount" NUMERIC(15,0) NOT NULL,
    "work_style" VARCHAR(20) NOT NULL,
    "work_form" VARCHAR(20) NOT NULL,
    "work_location" VARCHAR(255),
    "current_situation" VARCHAR(1500),
    "main_task" VARCHAR(1500),
    "detail_scope" VARCHAR(1500),
    "extra_note" VARCHAR(1500),
    "status" VARCHAR(30) DEFAULT 'REGISTERED' NOT NULL,
    "payment_status" VARCHAR(30) DEFAULT 'DEPOSIT_PENDING' NOT NULL,
    "total_headcount" INTEGER NOT NULL,
    "confirmed_headcount" INTEGER DEFAULT 0 NOT NULL,
    "recruit_started_at" TIMESTAMP,
    "recruit_deadline" TIMESTAMP,
    "extension_count" INTEGER DEFAULT 0 NOT NULL,
    "free_rerecommend_used" INTEGER DEFAULT 0 NOT NULL,
    "paid_rerecommend_used" INTEGER DEFAULT 0 NOT NULL,
    "notice_agreed_at" TIMESTAMP,
    "canceled_at" TIMESTAMP,
    "closed_at" TIMESTAMP,
    "retention_until" DATE,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "deleted_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "project_position" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_no" INTEGER NOT NULL,
    "job_category" VARCHAR(30) NOT NULL,
    "job_role" VARCHAR(40) NOT NULL,
    "min_career_years" INTEGER NOT NULL,
    "headcount" INTEGER NOT NULL,
    "preferred_note" VARCHAR(500),
    "confirmed_count" INTEGER DEFAULT 0 NOT NULL,
    "status" VARCHAR(30) DEFAULT 'RECRUITING' NOT NULL,
    "closed_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "position_skill" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "position_id" BIGINT NOT NULL,
    "skill_code" VARCHAR(50) NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "project_file" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "file_id" BIGINT NOT NULL,
    "sort_order" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "matching_round" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_id" BIGINT NOT NULL,
    "round_no" INTEGER NOT NULL,
    "round_type" VARCHAR(20) NOT NULL,
    "requested_count" INTEGER,
    "cost_amount" NUMERIC(15,0) DEFAULT 0 NOT NULL,
    "expose_count" INTEGER NOT NULL,
    "embedding_pool_size" INTEGER,
    "low_score_warned" BOOLEAN DEFAULT FALSE NOT NULL,
    "status" VARCHAR(20) DEFAULT 'RUNNING' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "matching_candidate" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "round_id" BIGINT NOT NULL,
    "position_id" BIGINT NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "stage" VARCHAR(20) NOT NULL,
    "similarity" NUMERIC(6,4),
    "base_score" NUMERIC(5,2),
    "grade_weight" NUMERIC(5,2),
    "fit_score" NUMERIC(5,2),
    "guard_passed" BOOLEAN,
    "guard_reason" VARCHAR(500),
    "fit_reason" TEXT,
    "rank_no" INTEGER,
    "is_exposed" BOOLEAN DEFAULT FALSE NOT NULL,
    -- 클라이언트가 이 후보를 거절(비활성 표시)했는지. 요청 발송 여부와는 무관하다.
    "is_rejected" BOOLEAN DEFAULT FALSE NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "matching_request" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_id" BIGINT NOT NULL,
    "candidate_id" BIGINT NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "status" VARCHAR(30) DEFAULT 'REQUEST_PENDING' NOT NULL,
    "requested_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "expires_at" TIMESTAMP NOT NULL,
    "responded_at" TIMESTAMP,
    "reject_reason" VARCHAR(255),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "matching_snapshot" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_id" BIGINT,
    "freelancer_id" BIGINT,
    "snapshot_type" VARCHAR(20) NOT NULL,
    "snapshot_json" JSONB NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "negotiation" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "request_id" BIGINT NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_id" BIGINT NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "status" VARCHAR(20) DEFAULT 'IN_PROGRESS' NOT NULL,
    "total_round" INTEGER DEFAULT 0 NOT NULL,
    "agreed_amount" NUMERIC(15,0),
    "budget_cap" NUMERIC(15,0) NOT NULL,
    "floor_amount" NUMERIC(15,0) NOT NULL,
    "ai_out_at" TIMESTAMP,
    "started_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "ended_at" TIMESTAMP,
    "end_reason" VARCHAR(255),
    "client_last_read_at" TIMESTAMP,
    "freelancer_last_read_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "negotiation_condition" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "negotiation_id" BIGINT NOT NULL,
    "condition_type" VARCHAR(30) NOT NULL,
    "client_value" VARCHAR(255),
    "freelancer_value" VARCHAR(255),
    "client_floor" VARCHAR(255),
    "freelancer_floor" VARCHAR(255),
    "agreed_value" VARCHAR(255),
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "round_count" INTEGER DEFAULT 0 NOT NULL,
    "sort_order" INTEGER DEFAULT 0 NOT NULL,
    "agreed_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "negotiation_message" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "negotiation_id" BIGINT NOT NULL,
    "condition_id" BIGINT,
    "round_no" INTEGER NOT NULL,
    "sender_type" VARCHAR(20) NOT NULL,
    "message_type" VARCHAR(20) NOT NULL,
    "content" TEXT NOT NULL,
    "reason" TEXT,
    "proposed_value" VARCHAR(255),
    "proposal_json" JSONB,
    "response" VARCHAR(20),
    "acting_account_id" BIGINT,
    "prev_hash" CHAR(64),
    "content_hash" CHAR(64),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "negotiation_approval" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "negotiation_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "party_role" VARCHAR(20) NOT NULL,
    "approved" BOOLEAN NOT NULL,
    "responded_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "chat_room" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "negotiation_id" BIGINT NOT NULL,
    "input_enabled" BOOLEAN DEFAULT FALSE NOT NULL,
    "status" VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "closed_at" TIMESTAMP,
    PRIMARY KEY ("id")
);

CREATE TABLE "chat_room_member" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "chat_room_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "party_role" VARCHAR(20) NOT NULL,
    "last_read_at" TIMESTAMP,
    "left_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "chat_message" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "chat_room_id" BIGINT NOT NULL,
    "sender_id" BIGINT,
    "content" VARCHAR(500) NOT NULL,
    "message_type" VARCHAR(20) DEFAULT 'TEXT' NOT NULL,
    "payload" JSONB,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "contract" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "contract_no" VARCHAR(50) NOT NULL,
    "negotiation_id" BIGINT NOT NULL,
    "project_id" BIGINT NOT NULL,
    "position_id" BIGINT NOT NULL,
    "client_id" BIGINT NOT NULL,
    "freelancer_id" BIGINT NOT NULL,
    "total_amount" NUMERIC(15,0) NOT NULL,
    "down_amount" NUMERIC(15,0) NOT NULL,
    "final_amount" NUMERIC(15,0) NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE NOT NULL,
    "work_style" VARCHAR(20) NOT NULL,
    "work_form" VARCHAR(20) NOT NULL,
    "work_location" VARCHAR(255),
    "inspection_days" INTEGER DEFAULT 7 NOT NULL,
    "payment_days" INTEGER DEFAULT 7 NOT NULL,
    "confidential_years" INTEGER DEFAULT 3 NOT NULL,
    "penalty_rate" NUMERIC(5,2) DEFAULT 10.00 NOT NULL,
    "special_terms" TEXT,
    "content_json" JSONB,
    "pdf_file_id" BIGINT,
    "esign_provider" VARCHAR(30),
    "esign_doc_id" VARCHAR(255),
    "status" VARCHAR(30) DEFAULT 'DRAFT' NOT NULL,
    "signed_at" TIMESTAMP,
    "completed_at" TIMESTAMP,
    "terminated_at" TIMESTAMP,
    "terminated_by" VARCHAR(20),
    "retention_until" DATE,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "contract_signature" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "contract_id" BIGINT NOT NULL,
    "account_id" BIGINT NOT NULL,
    "party_role" VARCHAR(20) NOT NULL,
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "verification_method" VARCHAR(30),
    "signed_at" TIMESTAMP,
    "ip_address" VARCHAR(45),
    "user_agent" VARCHAR(500),
    "timestamp_token" TEXT,
    "signature_file_id" BIGINT,
    "provider_signer_id" VARCHAR(255),
    "reject_reason" VARCHAR(255),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "virtual_account" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "balance" NUMERIC(15,0) DEFAULT 0 NOT NULL,
    "total_charged" NUMERIC(15,0) DEFAULT 0 NOT NULL,
    "total_withdrawn" NUMERIC(15,0) DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "ledger_entry" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "entry_no" VARCHAR(50) NOT NULL,
    "virtual_account_id" BIGINT NOT NULL,
    "direction" VARCHAR(10) NOT NULL,
    "entry_type" VARCHAR(30) NOT NULL,
    "amount" NUMERIC(15,0) NOT NULL,
    "balance_after" NUMERIC(15,0) NOT NULL,
    "ref_type" VARCHAR(30),
    "ref_id" BIGINT,
    "memo" VARCHAR(255),
    "status" VARCHAR(20) DEFAULT 'COMPLETED' NOT NULL,
    "fail_reason" VARCHAR(255),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "settlement" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "settlement_no" VARCHAR(50) NOT NULL,
    "project_id" BIGINT NOT NULL,
    "contract_id" BIGINT,
    "payer_account_id" BIGINT NOT NULL,
    "payer_role" VARCHAR(20) NOT NULL,
    "phase" VARCHAR(20) NOT NULL,
    "base_amount" NUMERIC(15,0) NOT NULL,
    "fee_rate" NUMERIC(5,2) NOT NULL,
    "grade_discount" NUMERIC(5,2) DEFAULT 0.00 NOT NULL,
    "fee_amount" NUMERIC(15,0) NOT NULL,
    "ledger_entry_id" BIGINT,
    "payment_method_id" BIGINT,
    "approval_no" VARCHAR(50),
    "fail_reason" VARCHAR(255),
    "overdue_reason" VARCHAR(255),
    "status" VARCHAR(30) DEFAULT 'PENDING' NOT NULL,
    "due_date" DATE,
    "paid_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "penalty" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "contract_id" BIGINT,
    "terminator_role" VARCHAR(20) NOT NULL,
    "payee_type" VARCHAR(20) NOT NULL,
    "worked_amount" NUMERIC(15,0) NOT NULL,
    "penalty_rate" NUMERIC(5,2) DEFAULT 10.00 NOT NULL,
    "penalty_amount" NUMERIC(15,0) NOT NULL,
    "payer_account_id" BIGINT NOT NULL,
    "payee_account_id" BIGINT,
    "ledger_entry_id" BIGINT,
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "paid_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "rerecommend_purchase" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "project_id" BIGINT NOT NULL,
    "round_id" BIGINT NOT NULL,
    "ledger_entry_id" BIGINT,
    "unit_price" NUMERIC(15,0) DEFAULT 10000 NOT NULL,
    "quantity" INTEGER NOT NULL,
    "amount" NUMERIC(15,0) NOT NULL,
    "status" VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 상호 평가. 작성 후 수정·삭제하지 않으므로 updated_at 이 없다.
CREATE TABLE "review" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "contract_id" BIGINT NOT NULL,
    "project_id" BIGINT NOT NULL,
    -- account.id 를 그대로 쓴다. 역할은 작성 시점 값을 함께 남겨 조인 없이 구분한다.
    "reviewer_account_id" BIGINT NOT NULL,
    "reviewer_role" VARCHAR(10) NOT NULL,
    "reviewee_account_id" BIGINT NOT NULL,
    "reviewee_role" VARCHAR(10) NOT NULL,
    "score" INTEGER NOT NULL,
    "content" VARCHAR(500),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 사이트 이용 후기.
--   후기 원문은 관리자 화면 밖으로 나가지 않는다. 사용자가 보는 것은 관리자가 홍보로 고른
--   후기와 평균 별점뿐이라, 노출을 정하는 스위치는 promoted 하나다.
--   메인 노출 조건은 promoted AND score >= 4 다.
--   (공개/비공개 컬럼은 없앴다 — 홍보를 끄면 이미 안 보여서 아무것도 바꾸지 않는 스위치였다)
CREATE TABLE "site_review" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "contract_id" BIGINT NOT NULL,
    "project_id" BIGINT NOT NULL,
    "writer_account_id" BIGINT NOT NULL,
    "writer_role" VARCHAR(10) NOT NULL,
    "score" INTEGER NOT NULL,
    "content" VARCHAR(500),
    "promoted" BOOLEAN DEFAULT FALSE NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 알림. 이동 경로는 link_url 하나로 내려주므로 ref_type/ref_id 를 따로 두지 않는다.
CREATE TABLE "notification" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "owner_account_id" BIGINT NOT NULL,
    "type" VARCHAR(30) NOT NULL,
    "title" VARCHAR(200) NOT NULL,
    "content" VARCHAR(500),
    "link_url" VARCHAR(300),
    "read" BOOLEAN DEFAULT FALSE NOT NULL,
    "read_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 챗봇 세션. 열고 닫는 개념이 없어 status/closed_at 을 두지 않는다.
-- 화면이 하루치 대화를 통째로 보여주므로 세션은 "이 대화가 누구 것인가"만 알면 된다.
CREATE TABLE "chatbot_session" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "owner_account_id" BIGINT NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 챗봇 대화. 한 행이 질문 1건과 그 답변이다(발신자별로 나누지 않는다).
--   intent 는 답변 아래 띄운 이동 버튼의 화면 코드다. 저장하지 않으면 버튼을 눌러
--   이동했다가 돌아왔을 때 버튼이 사라진다 — 즉 쓸수록 없어지는 화면이 된다.
--   CHECK 제약을 걸지 않는다. 화면이 하나 늘 때마다 운영에서 INSERT 가 막힌다.
CREATE TABLE "chatbot_message" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "session_id" BIGINT NOT NULL,
    "question" VARCHAR(500) NOT NULL,
    "answer" VARCHAR(2000) NOT NULL,
    "intent" VARCHAR(30),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "chatbot_quota" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "account_id" BIGINT NOT NULL,
    "quota_date" DATE NOT NULL,
    "used_count" INTEGER DEFAULT 0 NOT NULL,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 1:1 문의.
--   작성자 정보(writer_*)는 접수 시점 값을 복사해 둔다. 관리자 목록 검색을 account 조인 없이
--   이 테이블만으로 처리할 수 있고, 작성자가 탈퇴해도 문의 이력이 남는다.
--   문의 유형(category)은 스키마 v12 에서 제거했다.
CREATE TABLE "inquiry" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "writer_account_id" BIGINT NOT NULL,
    "writer_name" VARCHAR(100) NOT NULL,
    "writer_role" VARCHAR(20) NOT NULL,
    "writer_email" VARCHAR(100),
    "title" VARCHAR(200) NOT NULL,
    "content" VARCHAR(2000) NOT NULL,
    "status" VARCHAR(10) DEFAULT 'PENDING' NOT NULL,
    "answer" VARCHAR(2000),
    "answered_at" TIMESTAMP,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

-- 1:1 문의 첨부파일. 작성 화면에서 여러 건을 올릴 수 있다.
-- 1:1 문의 첨부파일. JPA @ElementCollection 매핑이라 자체 PK 가 없다.
-- (inquiry_id, sort_order) 가 한 행을 가리킨다.
CREATE TABLE "inquiry_file" (
    "inquiry_id" BIGINT NOT NULL,
    "file_id" BIGINT,
    "sort_order" INTEGER NOT NULL,
    PRIMARY KEY ("inquiry_id", "sort_order")
);

-- 상태 변경 이력. 관리자 상세 화면의 "상태 이력" 표에 쓴다.
-- 프로젝트/정산/계약이 같은 형태(일시·상태·처리자·비고)라 대상 종류를 컬럼으로 구분한다.
-- 대상 테이블이 여러 개여서 FK 는 걸지 않고 인덱스로만 조회한다.
CREATE TABLE "status_history" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "target_type" VARCHAR(20) NOT NULL,
    "target_id" BIGINT NOT NULL,
    "status" VARCHAR(30) NOT NULL,
    "actor_type" VARCHAR(20) NOT NULL,
    "actor_account_id" BIGINT,
    "note" VARCHAR(255),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);

CREATE TABLE "ai_agent_log" (
    "id" BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    "agent_type" VARCHAR(30) NOT NULL,
    "ref_type" VARCHAR(30),
    "ref_id" BIGINT,
    "model" VARCHAR(50),
    "request_json" JSONB,
    "response_json" JSONB,
    "prompt_tokens" INTEGER,
    "output_tokens" INTEGER,
    "cost_amount" NUMERIC(12,4),
    "latency_ms" INTEGER,
    "retry_count" INTEGER DEFAULT 0 NOT NULL,
    "status" VARCHAR(20) NOT NULL,
    "error_message" TEXT,
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY ("id")
);


-- =====================================================================
-- 2. 유니크 제약
-- =====================================================================

ALTER TABLE "terms" ADD CONSTRAINT "uk_terms_code_version" UNIQUE ("code", "version");
-- 역할이 다르면 같은 이메일/휴대폰으로 각각 가입할 수 있다. (클라이언트 계정 + 프리랜서 계정)
ALTER TABLE "account" ADD CONSTRAINT "uk_account_email_role" UNIQUE ("email", "role");
ALTER TABLE "account" ADD CONSTRAINT "uk_account_phone_role" UNIQUE ("phone", "role");
ALTER TABLE "social_account" ADD CONSTRAINT "uk_social_provider_uid" UNIQUE ("provider", "provider_uid");
ALTER TABLE "client_profile" ADD CONSTRAINT "uk_client_account" UNIQUE ("account_id");
ALTER TABLE "client_profile" ADD CONSTRAINT "uk_client_business_no" UNIQUE ("business_no");
ALTER TABLE "freelancer_profile" ADD CONSTRAINT "uk_freelancer_account" UNIQUE ("account_id");
-- 초안은 계정당 1건만 둔다. 저장할 때마다 덮어쓴다.
ALTER TABLE "resume_draft" ADD CONSTRAINT "uk_resume_draft_account" UNIQUE ("account_id");
ALTER TABLE "terms_agreement" ADD CONSTRAINT "uk_terms_agreement" UNIQUE ("account_id", "terms_id");
ALTER TABLE "freelancer_condition" ADD CONSTRAINT "uk_condition_freelancer" UNIQUE ("freelancer_id");
ALTER TABLE "condition_skill" ADD CONSTRAINT "uk_condition_skill" UNIQUE ("condition_id", "skill_code");
ALTER TABLE "resume" ADD CONSTRAINT "uk_resume_account" UNIQUE ("account_id");
ALTER TABLE "project_position" ADD CONSTRAINT "uk_project_position" UNIQUE ("project_id", "position_no");
ALTER TABLE "position_skill" ADD CONSTRAINT "uk_position_skill" UNIQUE ("position_id", "skill_code");
ALTER TABLE "project_file" ADD CONSTRAINT "uk_project_file" UNIQUE ("project_id", "file_id");
ALTER TABLE "matching_round" ADD CONSTRAINT "uk_matching_round" UNIQUE ("position_id", "round_no");
ALTER TABLE "matching_candidate" ADD CONSTRAINT "uk_matching_candidate" UNIQUE ("round_id", "freelancer_id");
ALTER TABLE "matching_request" ADD CONSTRAINT "uk_matching_request" UNIQUE ("position_id", "freelancer_id");
ALTER TABLE "negotiation" ADD CONSTRAINT "uk_negotiation_request" UNIQUE ("request_id");
ALTER TABLE "negotiation_condition" ADD CONSTRAINT "uk_negotiation_condition" UNIQUE ("negotiation_id", "condition_type");
ALTER TABLE "negotiation_approval" ADD CONSTRAINT "uk_negotiation_approval" UNIQUE ("negotiation_id", "account_id");
ALTER TABLE "chat_room" ADD CONSTRAINT "uk_chat_room_negotiation" UNIQUE ("negotiation_id");
ALTER TABLE "chat_room_member" ADD CONSTRAINT "uk_chat_room_member" UNIQUE ("chat_room_id", "account_id");
ALTER TABLE "contract" ADD CONSTRAINT "uk_contract_no" UNIQUE ("contract_no");
ALTER TABLE "contract" ADD CONSTRAINT "uk_contract_negotiation" UNIQUE ("negotiation_id");
ALTER TABLE "contract_signature" ADD CONSTRAINT "uk_contract_signature" UNIQUE ("contract_id", "account_id");
ALTER TABLE "virtual_account" ADD CONSTRAINT "uk_virtual_account" UNIQUE ("account_id");
ALTER TABLE "ledger_entry" ADD CONSTRAINT "uk_ledger_entry_no" UNIQUE ("entry_no");
ALTER TABLE "settlement" ADD CONSTRAINT "uk_settlement_no" UNIQUE ("settlement_no");
ALTER TABLE "rerecommend_purchase" ADD CONSTRAINT "uk_rerecommend_round" UNIQUE ("round_id");
ALTER TABLE "review" ADD CONSTRAINT "uk_review_contract_reviewer" UNIQUE ("contract_id", "reviewer_account_id");
ALTER TABLE "site_review" ADD CONSTRAINT "uk_site_review_contract_writer" UNIQUE ("contract_id", "writer_account_id");
ALTER TABLE "chatbot_quota" ADD CONSTRAINT "uk_chatbot_quota" UNIQUE ("account_id", "quota_date");

-- =====================================================================
-- 3. 외래키
-- =====================================================================

ALTER TABLE "social_account" ADD CONSTRAINT "fk_social_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "client_profile" ADD CONSTRAINT "fk_client_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "client_profile" ADD CONSTRAINT "fk_client_logo" FOREIGN KEY ("logo_file_id") REFERENCES "file" ("id");
ALTER TABLE "freelancer_profile" ADD CONSTRAINT "fk_freelancer_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "freelancer_profile" ADD CONSTRAINT "fk_freelancer_photo" FOREIGN KEY ("profile_file_id") REFERENCES "file" ("id");
ALTER TABLE "payment_method" ADD CONSTRAINT "fk_payment_method_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "terms_agreement" ADD CONSTRAINT "fk_agreement_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "terms_agreement" ADD CONSTRAINT "fk_agreement_terms" FOREIGN KEY ("terms_id") REFERENCES "terms" ("id");
ALTER TABLE "freelancer_condition" ADD CONSTRAINT "fk_condition_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "condition_skill" ADD CONSTRAINT "fk_condition_skill" FOREIGN KEY ("condition_id") REFERENCES "freelancer_condition" ("id");
ALTER TABLE "resume" ADD CONSTRAINT "fk_resume_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "resume" ADD CONSTRAINT "fk_resume_portfolio" FOREIGN KEY ("portfolio_file_id") REFERENCES "file" ("id");
ALTER TABLE "resume_education" ADD CONSTRAINT "fk_resume_education" FOREIGN KEY ("resume_id") REFERENCES "resume" ("id");
ALTER TABLE "resume_career" ADD CONSTRAINT "fk_resume_career" FOREIGN KEY ("resume_id") REFERENCES "resume" ("id");
ALTER TABLE "resume_certificate" ADD CONSTRAINT "fk_resume_certificate" FOREIGN KEY ("resume_id") REFERENCES "resume" ("id");
ALTER TABLE "resume_link" ADD CONSTRAINT "fk_resume_link" FOREIGN KEY ("resume_id") REFERENCES "resume" ("id");
ALTER TABLE "portfolio" ADD CONSTRAINT "fk_portfolio_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "account" ("id");
ALTER TABLE "portfolio" ADD CONSTRAINT "fk_portfolio_file" FOREIGN KEY ("file_id") REFERENCES "file" ("id");
ALTER TABLE "inquiry_file" ADD CONSTRAINT "fk_inquiry_file_inquiry" FOREIGN KEY ("inquiry_id") REFERENCES "inquiry" ("id");
ALTER TABLE "inquiry_file" ADD CONSTRAINT "fk_inquiry_file_file" FOREIGN KEY ("file_id") REFERENCES "file" ("id");
ALTER TABLE "settlement" ADD CONSTRAINT "fk_settlement_payment_method" FOREIGN KEY ("payment_method_id") REFERENCES "payment_method" ("id");
ALTER TABLE "project" ADD CONSTRAINT "fk_project_client" FOREIGN KEY ("client_id") REFERENCES "client_profile" ("id");
ALTER TABLE "project_position" ADD CONSTRAINT "fk_position_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "position_skill" ADD CONSTRAINT "fk_position_skill_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "project_file" ADD CONSTRAINT "fk_project_file_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "project_file" ADD CONSTRAINT "fk_project_file_file" FOREIGN KEY ("file_id") REFERENCES "file" ("id");
ALTER TABLE "matching_round" ADD CONSTRAINT "fk_matching_round_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "matching_round" ADD CONSTRAINT "fk_matching_round_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "matching_candidate" ADD CONSTRAINT "fk_candidate_round" FOREIGN KEY ("round_id") REFERENCES "matching_round" ("id");
ALTER TABLE "matching_candidate" ADD CONSTRAINT "fk_candidate_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "matching_candidate" ADD CONSTRAINT "fk_candidate_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "matching_request" ADD CONSTRAINT "fk_request_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "matching_request" ADD CONSTRAINT "fk_request_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "matching_request" ADD CONSTRAINT "fk_request_candidate" FOREIGN KEY ("candidate_id") REFERENCES "matching_candidate" ("id");
ALTER TABLE "matching_request" ADD CONSTRAINT "fk_request_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "matching_snapshot" ADD CONSTRAINT "fk_snapshot_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "matching_snapshot" ADD CONSTRAINT "fk_snapshot_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "matching_snapshot" ADD CONSTRAINT "fk_snapshot_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "negotiation" ADD CONSTRAINT "fk_negotiation_request" FOREIGN KEY ("request_id") REFERENCES "matching_request" ("id");
ALTER TABLE "negotiation" ADD CONSTRAINT "fk_negotiation_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "negotiation" ADD CONSTRAINT "fk_negotiation_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "negotiation" ADD CONSTRAINT "fk_negotiation_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "negotiation_condition" ADD CONSTRAINT "fk_condition_negotiation" FOREIGN KEY ("negotiation_id") REFERENCES "negotiation" ("id");
ALTER TABLE "negotiation_message" ADD CONSTRAINT "fk_message_negotiation" FOREIGN KEY ("negotiation_id") REFERENCES "negotiation" ("id");
ALTER TABLE "negotiation_message" ADD CONSTRAINT "fk_message_condition" FOREIGN KEY ("condition_id") REFERENCES "negotiation_condition" ("id");
ALTER TABLE "negotiation_approval" ADD CONSTRAINT "fk_approval_negotiation" FOREIGN KEY ("negotiation_id") REFERENCES "negotiation" ("id");
ALTER TABLE "negotiation_approval" ADD CONSTRAINT "fk_approval_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "chat_room" ADD CONSTRAINT "fk_chat_room_negotiation" FOREIGN KEY ("negotiation_id") REFERENCES "negotiation" ("id");
ALTER TABLE "chat_room_member" ADD CONSTRAINT "fk_chat_member_room" FOREIGN KEY ("chat_room_id") REFERENCES "chat_room" ("id");
ALTER TABLE "chat_room_member" ADD CONSTRAINT "fk_chat_member_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "chat_message" ADD CONSTRAINT "fk_chat_message_room" FOREIGN KEY ("chat_room_id") REFERENCES "chat_room" ("id");
ALTER TABLE "chat_message" ADD CONSTRAINT "fk_chat_message_sender" FOREIGN KEY ("sender_id") REFERENCES "account" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_negotiation" FOREIGN KEY ("negotiation_id") REFERENCES "negotiation" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_position" FOREIGN KEY ("position_id") REFERENCES "project_position" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_client" FOREIGN KEY ("client_id") REFERENCES "client_profile" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_freelancer" FOREIGN KEY ("freelancer_id") REFERENCES "freelancer_profile" ("id");
ALTER TABLE "contract" ADD CONSTRAINT "fk_contract_pdf" FOREIGN KEY ("pdf_file_id") REFERENCES "file" ("id");
ALTER TABLE "contract_signature" ADD CONSTRAINT "fk_signature_contract" FOREIGN KEY ("contract_id") REFERENCES "contract" ("id");
ALTER TABLE "contract_signature" ADD CONSTRAINT "fk_signature_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "contract_signature" ADD CONSTRAINT "fk_signature_file" FOREIGN KEY ("signature_file_id") REFERENCES "file" ("id");
ALTER TABLE "virtual_account" ADD CONSTRAINT "fk_virtual_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "ledger_entry" ADD CONSTRAINT "fk_ledger_virtual_account" FOREIGN KEY ("virtual_account_id") REFERENCES "virtual_account" ("id");
ALTER TABLE "settlement" ADD CONSTRAINT "fk_settlement_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "settlement" ADD CONSTRAINT "fk_settlement_contract" FOREIGN KEY ("contract_id") REFERENCES "contract" ("id");
ALTER TABLE "settlement" ADD CONSTRAINT "fk_settlement_payer" FOREIGN KEY ("payer_account_id") REFERENCES "account" ("id");
ALTER TABLE "settlement" ADD CONSTRAINT "fk_settlement_ledger" FOREIGN KEY ("ledger_entry_id") REFERENCES "ledger_entry" ("id");
ALTER TABLE "penalty" ADD CONSTRAINT "fk_penalty_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "penalty" ADD CONSTRAINT "fk_penalty_contract" FOREIGN KEY ("contract_id") REFERENCES "contract" ("id");
ALTER TABLE "penalty" ADD CONSTRAINT "fk_penalty_payer" FOREIGN KEY ("payer_account_id") REFERENCES "account" ("id");
ALTER TABLE "penalty" ADD CONSTRAINT "fk_penalty_payee" FOREIGN KEY ("payee_account_id") REFERENCES "account" ("id");
ALTER TABLE "penalty" ADD CONSTRAINT "fk_penalty_ledger" FOREIGN KEY ("ledger_entry_id") REFERENCES "ledger_entry" ("id");
ALTER TABLE "rerecommend_purchase" ADD CONSTRAINT "fk_rerecommend_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "rerecommend_purchase" ADD CONSTRAINT "fk_rerecommend_round" FOREIGN KEY ("round_id") REFERENCES "matching_round" ("id");
ALTER TABLE "rerecommend_purchase" ADD CONSTRAINT "fk_rerecommend_ledger" FOREIGN KEY ("ledger_entry_id") REFERENCES "ledger_entry" ("id");
ALTER TABLE "review" ADD CONSTRAINT "fk_review_contract" FOREIGN KEY ("contract_id") REFERENCES "contract" ("id");
ALTER TABLE "review" ADD CONSTRAINT "fk_review_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "review" ADD CONSTRAINT "fk_review_reviewer" FOREIGN KEY ("reviewer_account_id") REFERENCES "account" ("id");
ALTER TABLE "review" ADD CONSTRAINT "fk_review_reviewee" FOREIGN KEY ("reviewee_account_id") REFERENCES "account" ("id");
ALTER TABLE "site_review" ADD CONSTRAINT "fk_site_review_contract" FOREIGN KEY ("contract_id") REFERENCES "contract" ("id");
ALTER TABLE "site_review" ADD CONSTRAINT "fk_site_review_project" FOREIGN KEY ("project_id") REFERENCES "project" ("id");
ALTER TABLE "site_review" ADD CONSTRAINT "fk_site_review_writer" FOREIGN KEY ("writer_account_id") REFERENCES "account" ("id");
ALTER TABLE "chatbot_session" ADD CONSTRAINT "fk_chatbot_session_account" FOREIGN KEY ("owner_account_id") REFERENCES "account" ("id");
ALTER TABLE "chatbot_message" ADD CONSTRAINT "fk_chatbot_message_session" FOREIGN KEY ("session_id") REFERENCES "chatbot_session" ("id");
ALTER TABLE "chatbot_quota" ADD CONSTRAINT "fk_chatbot_quota_account" FOREIGN KEY ("account_id") REFERENCES "account" ("id");
ALTER TABLE "inquiry" ADD CONSTRAINT "fk_inquiry_writer" FOREIGN KEY ("writer_account_id") REFERENCES "account" ("id");

-- =====================================================================
-- 4. 인덱스
-- =====================================================================

CREATE INDEX "idx_file_group" ON "file" ("file_group");
CREATE INDEX "idx_file_uploader" ON "file" ("uploaded_by");
CREATE INDEX "idx_terms_target" ON "terms" ("target_role", "is_required");
CREATE INDEX "idx_account_role_status" ON "account" ("role", "status");
CREATE INDEX "idx_account_name_phone" ON "account" ("name", "phone");
CREATE INDEX "idx_account_created" ON "account" ("created_at");
-- 관리자 회원 목록의 "정지" 필터·요약 카드용. 정지 회원만 부분 인덱싱한다(대부분의 행은 NULL).
CREATE INDEX "idx_account_suspended" ON "account" ("suspended_at") WHERE "suspended_at" IS NOT NULL;
-- 재가입 제한도 역할별로 판정하므로 role 을 인덱스에 포함한다.
CREATE INDEX "idx_account_rejoin_email" ON "account" ("email_hash", "role", "rejoin_available_at");
CREATE INDEX "idx_account_rejoin_phone" ON "account" ("phone_hash", "role", "rejoin_available_at");
CREATE INDEX "idx_social_account" ON "social_account" ("account_id");
CREATE INDEX "idx_client_logo" ON "client_profile" ("logo_file_id");
CREATE INDEX "idx_client_grade" ON "client_profile" ("grade");
CREATE INDEX "idx_freelancer_photo" ON "freelancer_profile" ("profile_file_id");
CREATE INDEX "idx_freelancer_grade" ON "freelancer_profile" ("grade", "ai_matching_agreed");
CREATE INDEX "idx_payment_method_account" ON "payment_method" ("account_id", "method_type");
CREATE UNIQUE INDEX "uk_payment_method_default" ON "payment_method" ("account_id") WHERE "is_default" AND "deleted_at" IS NULL;
CREATE INDEX "idx_terms_agreement_terms" ON "terms_agreement" ("terms_id");
CREATE INDEX "idx_email_verification" ON "email_verification" ("email", "purpose", "created_at");
CREATE INDEX "idx_email_verification_expire" ON "email_verification" ("expires_at");
CREATE INDEX "idx_condition_role" ON "freelancer_condition" ("job_role");
CREATE INDEX "idx_condition_category" ON "freelancer_condition" ("job_category");
CREATE INDEX "idx_condition_match" ON "freelancer_condition" ("work_style", "work_form", "career_years");
CREATE INDEX "idx_condition_skill_lookup" ON "condition_skill" ("skill_code", "skill_level");
CREATE INDEX "idx_resume_status" ON "resume" ("status");
CREATE INDEX "idx_resume_portfolio" ON "resume" ("portfolio_file_id");
CREATE INDEX "idx_resume_education" ON "resume_education" ("resume_id", "sort_order");
CREATE INDEX "idx_resume_certificate" ON "resume_certificate" ("resume_id", "sort_order");
CREATE INDEX "idx_resume_link" ON "resume_link" ("resume_id");
CREATE INDEX "idx_portfolio_freelancer" ON "portfolio" ("freelancer_id", "sort_order");
CREATE INDEX "idx_inquiry_file" ON "inquiry_file" ("inquiry_id", "sort_order");
CREATE INDEX "idx_status_history_target" ON "status_history" ("target_type", "target_id", "created_at");
CREATE INDEX "idx_project_client_status" ON "project" ("client_id", "status");
CREATE INDEX "idx_project_status_created" ON "project" ("status", "created_at");
CREATE INDEX "idx_project_deadline" ON "project" ("recruit_deadline", "status");
CREATE INDEX "idx_position_role" ON "project_position" ("job_role", "status");
CREATE INDEX "idx_position_category" ON "project_position" ("job_category");
CREATE INDEX "idx_position_status" ON "project_position" ("status");
CREATE INDEX "idx_position_skill_skill" ON "position_skill" ("skill_code");
CREATE INDEX "idx_project_file_file" ON "project_file" ("file_id");
CREATE INDEX "idx_matching_round_project" ON "matching_round" ("project_id", "round_type");
CREATE INDEX "idx_matching_round_status" ON "matching_round" ("status");
CREATE INDEX "idx_candidate_dedupe" ON "matching_candidate" ("position_id", "freelancer_id");
CREATE INDEX "idx_candidate_freelancer" ON "matching_candidate" ("freelancer_id");
CREATE INDEX "idx_candidate_rank" ON "matching_candidate" ("round_id", "is_exposed", "rank_no");
CREATE INDEX "idx_request_project_status" ON "matching_request" ("project_id", "status");
CREATE INDEX "idx_request_freelancer_status" ON "matching_request" ("freelancer_id", "status");
CREATE INDEX "idx_request_expire" ON "matching_request" ("status", "expires_at");
CREATE INDEX "idx_request_candidate" ON "matching_request" ("candidate_id");
CREATE INDEX "idx_snapshot_project" ON "matching_snapshot" ("project_id", "snapshot_type");
CREATE INDEX "idx_snapshot_position" ON "matching_snapshot" ("position_id");
CREATE INDEX "idx_snapshot_freelancer" ON "matching_snapshot" ("freelancer_id");
CREATE INDEX "idx_negotiation_project_status" ON "negotiation" ("project_id", "status");
CREATE INDEX "idx_negotiation_position" ON "negotiation" ("position_id");
CREATE INDEX "idx_negotiation_freelancer" ON "negotiation" ("freelancer_id", "status");
CREATE INDEX "idx_condition_status" ON "negotiation_condition" ("negotiation_id", "status", "sort_order");
CREATE INDEX "idx_negotiation_message" ON "negotiation_message" ("negotiation_id", "created_at");
CREATE INDEX "idx_negotiation_message_cond" ON "negotiation_message" ("condition_id", "round_no");
CREATE INDEX "idx_approval_account" ON "negotiation_approval" ("account_id");
CREATE INDEX "idx_chat_member_account" ON "chat_room_member" ("account_id", "left_at");
CREATE INDEX "idx_chat_message_room" ON "chat_message" ("chat_room_id", "created_at");
CREATE INDEX "idx_chat_message_sender" ON "chat_message" ("sender_id");
CREATE INDEX "idx_contract_project_status" ON "contract" ("project_id", "status");
CREATE INDEX "idx_contract_client" ON "contract" ("client_id", "status");
CREATE INDEX "idx_contract_freelancer" ON "contract" ("freelancer_id", "status");
CREATE INDEX "idx_contract_position" ON "contract" ("position_id");
CREATE INDEX "idx_contract_pdf" ON "contract" ("pdf_file_id");
CREATE INDEX "idx_contract_retention" ON "contract" ("retention_until");
CREATE INDEX "idx_signature_account" ON "contract_signature" ("account_id", "status");
CREATE INDEX "idx_signature_file" ON "contract_signature" ("signature_file_id");
CREATE INDEX "idx_ledger_account" ON "ledger_entry" ("virtual_account_id", "created_at");
CREATE INDEX "idx_ledger_ref" ON "ledger_entry" ("ref_type", "ref_id");
CREATE INDEX "idx_ledger_type" ON "ledger_entry" ("entry_type", "created_at");
CREATE INDEX "idx_settlement_project" ON "settlement" ("project_id", "phase");
CREATE INDEX "idx_settlement_contract" ON "settlement" ("contract_id");
CREATE INDEX "idx_settlement_payer" ON "settlement" ("payer_account_id", "status");
CREATE INDEX "idx_settlement_status_due" ON "settlement" ("status", "due_date");
CREATE INDEX "idx_settlement_ledger" ON "settlement" ("ledger_entry_id");
CREATE INDEX "idx_penalty_project" ON "penalty" ("project_id");
CREATE INDEX "idx_penalty_contract" ON "penalty" ("contract_id", "payee_type");
CREATE INDEX "idx_penalty_payer" ON "penalty" ("payer_account_id", "status");
CREATE INDEX "idx_penalty_payee" ON "penalty" ("payee_account_id");
CREATE INDEX "idx_penalty_ledger" ON "penalty" ("ledger_entry_id");
CREATE INDEX "idx_rerecommend_project" ON "rerecommend_purchase" ("project_id", "status");
CREATE INDEX "idx_rerecommend_ledger" ON "rerecommend_purchase" ("ledger_entry_id");
CREATE INDEX "idx_review_reviewee" ON "review" ("reviewee_account_id", "created_at");
CREATE INDEX "idx_review_reviewer" ON "review" ("reviewer_account_id");
CREATE INDEX "idx_review_project" ON "review" ("project_id");
-- 비로그인 메인 노출 조회: promoted AND score>=4, 최신순
CREATE INDEX "idx_site_review_home" ON "site_review" ("promoted", "score", "created_at");
CREATE INDEX "idx_site_review_writer" ON "site_review" ("writer_account_id");
CREATE INDEX "idx_notification_owner" ON "notification" ("owner_account_id", "read", "created_at");
CREATE INDEX "idx_chatbot_session_account" ON "chatbot_session" ("owner_account_id");
CREATE INDEX "idx_chatbot_message_session" ON "chatbot_message" ("session_id", "created_at");
CREATE INDEX "idx_inquiry_writer" ON "inquiry" ("writer_account_id", "status");
CREATE INDEX "idx_inquiry_status" ON "inquiry" ("status", "created_at");
CREATE INDEX "idx_ai_log_type" ON "ai_agent_log" ("agent_type", "created_at");
CREATE INDEX "idx_ai_log_ref" ON "ai_agent_log" ("ref_type", "ref_id");
CREATE INDEX "idx_ai_log_status" ON "ai_agent_log" ("status", "created_at");
CREATE INDEX "idx_resume_career" ON "resume_career" ("resume_id");

-- =====================================================================
-- 5. 테이블 설명
-- =====================================================================

COMMENT ON TABLE "file" IS 'S3 업로드 파일 메타';
COMMENT ON TABLE "terms" IS '약관(버전별 이력 보관)';
COMMENT ON TABLE "account" IS '통합 계정(인증·정지·탈퇴 단일 소스). 탈퇴 시 email 을 더미값으로 바꾸고 원본은 email_hash 로만 보관';
COMMENT ON TABLE "social_account" IS '소셜 로그인 연동(프리랜서 전용)';
COMMENT ON TABLE "client_profile" IS '클라이언트(국내 기업) 프로필';
COMMENT ON TABLE "freelancer_profile" IS '프리랜서(개인) 프로필';
COMMENT ON TABLE "payment_method" IS '결제/정산 수단(카드·계좌 모두 암호화 저장)';
COMMENT ON TABLE "terms_agreement" IS '약관 동의 이력(분쟁 시 증거)';
COMMENT ON TABLE "email_verification" IS '이메일 인증 코드(유효 3분)';
COMMENT ON TABLE "freelancer_condition" IS '프리랜서 등록 조건(화면1). 매칭 조건의 기준 데이터';
COMMENT ON TABLE "condition_skill" IS '보유 스킬(숙련도 포함, 1개 이상). 포지션 요구 스킬과 대조하는 매칭 기준';
COMMENT ON TABLE "resume" IS '이력서(화면2). 성명·생년월일은 account/freelancer_profile 에서 가져오며 수정 불가';
COMMENT ON COLUMN "resume"."account_id" IS 'account FK(1:1)';
COMMENT ON COLUMN "resume"."profile_file_id" IS '프로필 사진 file FK(필수)';
COMMENT ON TABLE "resume_education" IS '학력사항';
COMMENT ON TABLE "resume_career" IS '경력사항';
COMMENT ON TABLE "resume_certificate" IS '자격증 및 어학';
COMMENT ON TABLE "resume_link" IS '외부 링크';
COMMENT ON TABLE "project" IS '프로젝트 공고';
COMMENT ON TABLE "project_position" IS '모집 포지션(인원별 조건). confirmed_count=headcount 시 FILLED로 전환해 추가 매칭 중단';
COMMENT ON TABLE "position_skill" IS '포지션 요구 스킬. 스킬은 enum 코드값';
COMMENT ON TABLE "project_file" IS '프로젝트 첨부자료 N:M';
COMMENT ON TABLE "matching_round" IS '매칭 회차(포지션 단위 재추천)';
COMMENT ON TABLE "matching_candidate" IS '매칭 후보 이력(동일 프로젝트 재추천 시 중복 노출 방지 근거)';
COMMENT ON TABLE "matching_request" IS '매칭 요청/응답 + 인원별 진행 상태';
COMMENT ON TABLE "matching_snapshot" IS '매칭 시점 정보 동결(AI 매칭 기준 데이터)';
COMMENT ON TABLE "negotiation" IS '협상 세션(프리랜서가 개별 수락하면 즉시 개시. 전원 수락 불필요)';
COMMENT ON TABLE "negotiation_condition" IS '협상 조건(불일치 항목만 생성)';
COMMENT ON TABLE "negotiation_message" IS '협상 로그(제안·근거·응답). 투명성 확보용 조회 대상';
COMMENT ON TABLE "negotiation_approval" IS '15회 소진 시 최종 승인 응답';
COMMENT ON TABLE "chat_room" IS '협상 채팅방';
COMMENT ON TABLE "chat_room_member" IS '채팅방 참여자';
COMMENT ON TABLE "chat_message" IS '사람 채팅 메시지(AI Out 이후 활성화)';
COMMENT ON TABLE "contract" IS '표준계약서(포지션 1자리당 1건)';
COMMENT ON TABLE "contract_signature" IS '계약 전자서명(양측 완료 시 체결)';
COMMENT ON TABLE "virtual_account" IS '가상 계좌 잔액(모의 결제. 실제 PG 연동 없음)';
COMMENT ON TABLE "ledger_entry" IS '잔액 증감 원장(모의 결제 트랜잭션)';
COMMENT ON TABLE "settlement" IS '플랫폼 수수료 정산(용역비는 당사자 직거래)';
COMMENT ON TABLE "penalty" IS '중도 파기 위약금(상대방 10% + 플랫폼 10%)';
COMMENT ON TABLE "rerecommend_purchase" IS '유료 재추천 구매(프로젝트당 최대 5회)';
COMMENT ON TABLE "review" IS '상호 평가(양방향, 등급 산정 기준). 프로젝트 [종료] 상태 이후에만 작성 가능';
COMMENT ON COLUMN "review"."reviewer_account_id" IS '작성자 account FK';
COMMENT ON COLUMN "review"."reviewer_role" IS 'CLIENT / FREELANCER. 작성 시점 역할';
COMMENT ON COLUMN "review"."reviewee_account_id" IS '대상자 account FK';
COMMENT ON COLUMN "review"."reviewee_role" IS 'CLIENT / FREELANCER';
COMMENT ON COLUMN "review"."score" IS '별점 1~5(1점 단위, 필수)';
COMMENT ON TABLE "site_review" IS '사이트 이용후기(관리자가 홍보로 켠 것만 사용자에게 노출)';
COMMENT ON COLUMN "site_review"."writer_account_id" IS '작성자 account FK';
COMMENT ON COLUMN "site_review"."score" IS '별점 1~5(필수)';
COMMENT ON COLUMN "site_review"."promoted" IS '홍보 활용 여부. 관리자가 선별한다. 노출을 정하는 유일한 스위치';
COMMENT ON TABLE "notification" IS '알림(사용자 삭제 시 즉시 hard delete)';
COMMENT ON COLUMN "notification"."owner_account_id" IS '수신자 account FK';
COMMENT ON COLUMN "notification"."read" IS '읽음 여부';
COMMENT ON TABLE "chatbot_session" IS '고객 문의 챗봇 세션(협상 채팅과 별개). 1:1 문의와 독립된 창구이며 사용자가 선택한다';
COMMENT ON TABLE "chatbot_message" IS '챗봇 대화';
COMMENT ON TABLE "chatbot_quota" IS '챗봇 일일 사용 한도';
COMMENT ON TABLE "inquiry" IS '1:1 문의. 챗봇 이용 여부와 무관하게 언제든 접수 가능한 독립 창구이며 사용자가 선택한다';
COMMENT ON COLUMN "inquiry"."writer_account_id" IS '문의자 account FK';
COMMENT ON COLUMN "inquiry"."writer_name" IS '접수 시점 작성자명 스냅샷';
COMMENT ON COLUMN "inquiry"."writer_role" IS '접수 시점 회원유형 스냅샷';
COMMENT ON COLUMN "inquiry"."writer_email" IS '접수 시점 이메일 스냅샷';
COMMENT ON TABLE "ai_agent_log" IS 'AI 에이전트 동작 로그. 관리자가 추천 근거·제안·이유·응답·오류를 조회. 보존은 데이터 보존 정책을 따름';

-- =====================================================================
-- 6. 컬럼 설명
-- =====================================================================


COMMENT ON COLUMN "file"."id" IS 'PK';
COMMENT ON COLUMN "file"."file_group" IS 'PROFILE_IMAGE / COMPANY_LOGO / PORTFOLIO / PROJECT_DOC / DELIVERABLE / CONTRACT_PDF / SIGNATURE';
COMMENT ON COLUMN "file"."original_name" IS '업로드 원본 파일명';
COMMENT ON COLUMN "file"."storage_key" IS 'S3 오브젝트 키';
COMMENT ON COLUMN "file"."mime_type" IS 'MIME 타입';
COMMENT ON COLUMN "file"."size_bytes" IS '파일 크기(byte)';
COMMENT ON COLUMN "file"."uploaded_by" IS '업로더 account.id';

COMMENT ON COLUMN "terms"."id" IS 'PK';
COMMENT ON COLUMN "terms"."code" IS 'SERVICE / PRIVACY_CONSENT / MARKETING / PRIVACY_POLICY';
COMMENT ON COLUMN "terms"."terms_type" IS 'AGREEMENT(가입 동의 항목) / POLICY(게시 문서, 동의 대상 아님)';
COMMENT ON COLUMN "terms"."version" IS '약관 버전(v1.0)';
COMMENT ON COLUMN "terms"."title" IS '약관 제목';
COMMENT ON COLUMN "terms"."content" IS '약관 전문(버전별 INSERT, UPDATE 금지)';
COMMENT ON COLUMN "terms"."is_required" IS '필수 동의 여부';
COMMENT ON COLUMN "terms"."target_role" IS 'CLIENT / FREELANCER / NULL(공통)';
COMMENT ON COLUMN "terms"."effective_at" IS '시행일';

COMMENT ON COLUMN "account"."id" IS 'PK';
COMMENT ON COLUMN "account"."email" IS '로그인 아이디(소문자 정규화 저장). role 과 묶어서 유니크';
COMMENT ON COLUMN "account"."password_hash" IS 'BCrypt 해시. 소셜 전용 계정은 NULL';
COMMENT ON COLUMN "account"."role" IS 'CLIENT / FREELANCER / ADMIN';
COMMENT ON COLUMN "account"."name" IS '이름(클라=대표자명). 수정 불가';
COMMENT ON COLUMN "account"."phone" IS '휴대폰번호(숫자만 정규화 저장). role 과 묶어서 유니크';
COMMENT ON COLUMN "account"."signup_type" IS 'EMAIL / SOCIAL';
COMMENT ON COLUMN "account"."status" IS 'PENDING / ACTIVE / LOCKED / WITHDRAWN. 정지 상태는 Redis 에서 관리';
COMMENT ON COLUMN "account"."email_verified" IS '이메일 인증 완료 여부';
COMMENT ON COLUMN "account"."login_fail_count" IS '연속 로그인 실패 횟수(5회 잠금)';
COMMENT ON COLUMN "account"."locked_at" IS '계정 잠금 시각';
COMMENT ON COLUMN "account"."is_temp_password" IS '임시 비밀번호 상태(로그인 후 변경 강제)';
COMMENT ON COLUMN "account"."password_updated_at" IS '비밀번호 최종 변경 시각';
COMMENT ON COLUMN "account"."last_login_at" IS '최종 로그인 시각';
COMMENT ON COLUMN "account"."suspended_at" IS '관리자 정지 시각. NULL 이 아니면 정지 상태(관리자 서버 전용 · 로그인 차단 판정은 Redis SUSPEND:{id})';
COMMENT ON COLUMN "account"."suspend_reason" IS '정지 사유. 정지된 사용자에게 안내';
COMMENT ON COLUMN "account"."withdrawn_at" IS '탈퇴 시각';
COMMENT ON COLUMN "account"."withdraw_reason" IS '탈퇴 사유';
COMMENT ON COLUMN "account"."email_hash" IS '탈퇴 시 원본 이메일 SHA-256(재가입 30일 판정용)';
COMMENT ON COLUMN "account"."phone_hash" IS '탈퇴 시 원본 전화번호 SHA-256';
COMMENT ON COLUMN "account"."rejoin_available_at" IS '재가입 가능 시각(탈퇴 +30일)';
COMMENT ON COLUMN "account"."purge_at" IS '등록 내용 삭제 예정일(탈퇴 +1년)';
COMMENT ON COLUMN "account"."deleted_at" IS 'soft delete 표시';

COMMENT ON COLUMN "social_account"."id" IS 'PK';
COMMENT ON COLUMN "social_account"."account_id" IS '계정 FK';
COMMENT ON COLUMN "social_account"."provider" IS 'KAKAO / GOOGLE';
COMMENT ON COLUMN "social_account"."provider_uid" IS '공급자 고유 ID(카카오 id / 구글 sub)';
COMMENT ON COLUMN "social_account"."provider_email" IS '공급자 제공 이메일(마스킹·미인증 가능)';
COMMENT ON COLUMN "social_account"."email_verified" IS '공급자 이메일 인증 여부';

COMMENT ON COLUMN "client_profile"."id" IS 'PK';
COMMENT ON COLUMN "client_profile"."account_id" IS '계정 FK(1:1)';
COMMENT ON COLUMN "client_profile"."company_name" IS '기업명(수정 가능)';
COMMENT ON COLUMN "client_profile"."business_no" IS '사업자등록번호(하이픈 없는 10자리, 국세청 API 검증)';
COMMENT ON COLUMN "client_profile"."business_field" IS '사업 분야 코드 20종(IT_CONTENTS_AI / GAME / FINANCE / MEDICAL 등)';
COMMENT ON COLUMN "client_profile"."employee_count" IS '직원수 구간 코드(UNDER_10 / 10_49 / 50_99 / 100_299 / OVER_300)';
COMMENT ON COLUMN "client_profile"."address" IS '기업 주소(계약서 갑 표시용)';
COMMENT ON COLUMN "client_profile"."logo_file_id" IS '기업 사진 FK';
COMMENT ON COLUMN "client_profile"."grade" IS 'SILVER / GOLD / DIAMOND';
COMMENT ON COLUMN "client_profile"."grade_checked_at" IS '최근 등급 산정 시각(월 1회 배치)';

COMMENT ON COLUMN "freelancer_profile"."id" IS 'PK';
COMMENT ON COLUMN "freelancer_profile"."account_id" IS '계정 FK(1:1)';
COMMENT ON COLUMN "freelancer_profile"."birth_date" IS '생년월일(만 18세 이상, 연도 하드코딩 금지). 수정 불가';
COMMENT ON COLUMN "freelancer_profile"."address" IS '주소(수정 가능)';
COMMENT ON COLUMN "freelancer_profile"."profile_file_id" IS '프로필 사진 FK(5MB 이하)';
COMMENT ON COLUMN "freelancer_profile"."ai_matching_agreed" IS 'AI 매칭 대상 포함 여부(본인 선택)';
COMMENT ON COLUMN "freelancer_profile"."grade" IS 'JUNIOR / SENIOR / MASTER';
COMMENT ON COLUMN "freelancer_profile"."grade_checked_at" IS '최근 등급 산정 시각(월 1회 배치)';

COMMENT ON COLUMN "payment_method"."id" IS 'PK';
COMMENT ON COLUMN "payment_method"."account_id" IS '계정 FK';
COMMENT ON COLUMN "payment_method"."method_type" IS 'CARD(수수료 결제) / BANK_ACCOUNT(용역비 수령)';
COMMENT ON COLUMN "payment_method"."card_number_enc" IS '카드번호 암호화(AES, KMS 키 관리). ※평문 저장 금지';
COMMENT ON COLUMN "payment_method"."card_brand" IS '카드사명';
COMMENT ON COLUMN "payment_method"."card_last4" IS '카드 끝 4자리(마이페이지 마스킹 표시용)';
COMMENT ON COLUMN "payment_method"."bank_code" IS '은행 코드';
COMMENT ON COLUMN "payment_method"."account_no_enc" IS '계좌번호 암호화(AES, KMS 키 관리)';
COMMENT ON COLUMN "payment_method"."account_holder" IS '예금주';

COMMENT ON COLUMN "terms_agreement"."id" IS 'PK';
COMMENT ON COLUMN "terms_agreement"."account_id" IS '계정 FK';
COMMENT ON COLUMN "terms_agreement"."terms_id" IS '약관 FK(버전 포함)';
COMMENT ON COLUMN "terms_agreement"."agreed" IS '동의 여부';
COMMENT ON COLUMN "terms_agreement"."agreed_at" IS '동의 시각';
COMMENT ON COLUMN "terms_agreement"."user_agent" IS '동의 시점 User-Agent';

COMMENT ON COLUMN "email_verification"."id" IS 'PK';
COMMENT ON COLUMN "email_verification"."email" IS '대상 이메일';
COMMENT ON COLUMN "email_verification"."purpose" IS 'SIGNUP / UNLOCK / PROFILE_UPDATE. 비밀번호 초기화는 링크 방식이라 여기 포함하지 않음';
COMMENT ON COLUMN "email_verification"."code_hash" IS '인증코드 해시(평문 저장 금지)';
COMMENT ON COLUMN "email_verification"."expires_at" IS '만료 시각(발송 +3분)';
COMMENT ON COLUMN "email_verification"."verified_at" IS '인증 완료 시각';
COMMENT ON COLUMN "email_verification"."attempt_count" IS '코드 입력 시도 횟수(5회 초과 시 폐기)';

COMMENT ON COLUMN "freelancer_condition"."id" IS 'PK';
COMMENT ON COLUMN "freelancer_condition"."freelancer_id" IS 'freelancer_profile FK(1:1)';
COMMENT ON COLUMN "freelancer_condition"."job_category" IS '직군 코드(DEVELOPMENT / DESIGN). Java enum JobCategory';
COMMENT ON COLUMN "freelancer_condition"."job_role" IS '직무 코드 26종(BACKEND_DEV / UX_UI_DESIGNER ...). Java enum JobRole';
COMMENT ON COLUMN "freelancer_condition"."affiliation" IS '소속(선택)';
COMMENT ON COLUMN "freelancer_condition"."work_style" IS 'REMOTE(재택) / ONSITE(상주) / ANY(모두가능)';
COMMENT ON COLUMN "freelancer_condition"."work_form" IS 'FULL_TIME / PART_TIME / ANY';
COMMENT ON COLUMN "freelancer_condition"."pay_unit" IS 'HOURLY(시급) / DAILY(일급) / MONTHLY(월급)';
COMMENT ON COLUMN "freelancer_condition"."pay_amount" IS '희망 급여(원). 만원 단위 입력, 최소 10000';
COMMENT ON COLUMN "freelancer_condition"."min_accept_amount" IS '최저 수용가(협상 하한 가드)';
COMMENT ON COLUMN "freelancer_condition"."available_from" IS '프로젝트 시작 가능일';
COMMENT ON COLUMN "freelancer_condition"."start_negotiable" IS '시작일 협의 가능';
COMMENT ON COLUMN "freelancer_condition"."period_value" IS '희망 예상 기간 값(최대 24)';
COMMENT ON COLUMN "freelancer_condition"."period_unit" IS 'MONTH / WEEK';
COMMENT ON COLUMN "freelancer_condition"."has_freelance_exp" IS '프리랜서 경험 유무';
COMMENT ON COLUMN "freelancer_condition"."career_years" IS '경력 연차(최소 1)';

COMMENT ON COLUMN "condition_skill"."id" IS 'PK';
COMMENT ON COLUMN "condition_skill"."condition_id" IS '등록 조건 FK';
COMMENT ON COLUMN "condition_skill"."skill_code" IS '스킬 코드(REACT / SPRING_BOOT ...). Java enum Skill';
COMMENT ON COLUMN "condition_skill"."skill_level" IS 'BEGINNER(초급) / INTERMEDIATE(중급) / ADVANCED(고급)';

COMMENT ON COLUMN "resume"."id" IS 'PK';
COMMENT ON COLUMN "resume"."contact_phone" IS '이력서 표시용 연락처. 비우면 account.phone 사용';
COMMENT ON COLUMN "resume"."contact_email" IS '이력서 표시용 이메일. 로그인 계정과 별개로 수정 가능';
COMMENT ON COLUMN "resume"."self_introduction" IS '간단 자기소개';
COMMENT ON COLUMN "resume"."portfolio_file_id" IS '포트폴리오 PDF FK(20MB 권장)';
COMMENT ON COLUMN "resume"."status" IS 'DRAFT(임시저장) / COMPLETED';

COMMENT ON COLUMN "resume_education"."id" IS 'PK';
COMMENT ON COLUMN "resume_education"."resume_id" IS '이력서 FK';
COMMENT ON COLUMN "resume_education"."start_date" IS '입학일';
COMMENT ON COLUMN "resume_education"."end_date" IS '졸업일';
COMMENT ON COLUMN "resume_education"."school_name" IS '학교명';
COMMENT ON COLUMN "resume_education"."major" IS '전공';
COMMENT ON COLUMN "resume_education"."graduation_status" IS 'GRADUATED / ENROLLED / LEAVE / DROPOUT / EXPECTED';
COMMENT ON COLUMN "resume_education"."campus_type" IS 'MAIN(본교) / BRANCH(분교)';

COMMENT ON COLUMN "resume_career"."id" IS 'PK';
COMMENT ON COLUMN "resume_career"."resume_id" IS '이력서 FK';
COMMENT ON COLUMN "resume_career"."start_date" IS '근무 시작일';
COMMENT ON COLUMN "resume_career"."end_date" IS '근무 종료일(NULL=재직중)';
COMMENT ON COLUMN "resume_career"."company_name" IS '회사/기관명';
COMMENT ON COLUMN "resume_career"."department" IS '부서';
COMMENT ON COLUMN "resume_career"."position" IS '직급';
COMMENT ON COLUMN "resume_career"."job_description" IS '담당 업무';

COMMENT ON COLUMN "resume_certificate"."id" IS 'PK';
COMMENT ON COLUMN "resume_certificate"."resume_id" IS '이력서 FK';
COMMENT ON COLUMN "resume_certificate"."acquired_date" IS '취득일자';
COMMENT ON COLUMN "resume_certificate"."name" IS '자격/어학 시험명';
COMMENT ON COLUMN "resume_certificate"."note" IS '비고';

COMMENT ON COLUMN "resume_link"."id" IS 'PK';
COMMENT ON COLUMN "resume_link"."resume_id" IS '이력서 FK';
COMMENT ON COLUMN "resume_link"."url" IS '외부 링크 URL(깃허브·포트폴리오·노션 등). 종류 구분 없이 URL 만 저장';

COMMENT ON COLUMN "project"."id" IS 'PK';
COMMENT ON COLUMN "project"."client_id" IS 'client_profile FK';
COMMENT ON COLUMN "project"."title" IS '프로젝트 이름';
COMMENT ON COLUMN "project"."start_desired_date" IS '시작 희망일';
COMMENT ON COLUMN "project"."start_negotiable" IS '시작일 협의 가능';
COMMENT ON COLUMN "project"."period_value" IS '예상 기간 값(최대 24)';
COMMENT ON COLUMN "project"."period_unit" IS 'MONTH / WEEK';
COMMENT ON COLUMN "project"."budget_amount" IS '예산(원). 500만~10억, 부가세 별도';
COMMENT ON COLUMN "project"."work_style" IS 'REMOTE / ONSITE / ANY';
COMMENT ON COLUMN "project"."work_form" IS 'FULL_TIME / PART_TIME / ANY';
COMMENT ON COLUMN "project"."work_location" IS '근무 장소(상주 시)';
COMMENT ON COLUMN "project"."current_situation" IS '현재 프로젝트 진행 상황(1500자)';
COMMENT ON COLUMN "project"."main_task" IS '주요 담당 업무(1500자)';
COMMENT ON COLUMN "project"."detail_scope" IS '세부 업무범위(1500자)';
COMMENT ON COLUMN "project"."extra_note" IS '기타 전달사항/우대사항(1500자)';
COMMENT ON COLUMN "project"."status" IS 'REGISTERED(등록완료) / RECRUITING(모집중) / NEGOTIATING(협상중) / CONTRACT_PENDING(계약대기) / IN_PROGRESS(진행중) / COMPLETION_PENDING(완료대기) / CLOSED(종료) / CANCELED(취소됨. 모집 기간 최대 연장 초과 후 미확정 시 클라 파기 판정)';
COMMENT ON COLUMN "project"."payment_status" IS '결제 상태 5종: DEPOSIT_PENDING / DEPOSIT_PAID / SUCCESS_FEE_PENDING / SUCCESS_FEE_PAID / PAYMENT_FAILED';
COMMENT ON COLUMN "project"."total_headcount" IS '총 모집 인원(등록 후 증가 불가)';
COMMENT ON COLUMN "project"."confirmed_headcount" IS '계약 완료 인원. total_headcount 와 같아져야 IN_PROGRESS 로 전환';
COMMENT ON COLUMN "project"."recruit_started_at" IS '모집 시작(착수금 수수료 결제 완료 시각)';
COMMENT ON COLUMN "project"."recruit_deadline" IS '모집 마감(기본 2주, 연장 반영)';
COMMENT ON COLUMN "project"."extension_count" IS '모집 기간 연장 횟수(최대 2)';
COMMENT ON COLUMN "project"."free_rerecommend_used" IS '무료 재추천 사용 횟수(최대 1)';
COMMENT ON COLUMN "project"."paid_rerecommend_used" IS '유료 재추천 사용 횟수(최대 5)';
COMMENT ON COLUMN "project"."notice_agreed_at" IS '등록 전 안내 동의 시각';
COMMENT ON COLUMN "project"."canceled_at" IS '취소 시각';
COMMENT ON COLUMN "project"."closed_at" IS '종료 시각';
COMMENT ON COLUMN "project"."retention_until" IS '보존 만료일(종료 +5년)';
COMMENT ON COLUMN "project"."deleted_at" IS '매칭 전 탈퇴/삭제 시 즉시 hard delete, 이후는 보존';

COMMENT ON COLUMN "project_position"."id" IS 'PK';
COMMENT ON COLUMN "project_position"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "project_position"."position_no" IS '포지션 번호(1부터)';
COMMENT ON COLUMN "project_position"."job_category" IS '직군 코드(DEVELOPMENT / DESIGN). Java enum JobCategory';
COMMENT ON COLUMN "project_position"."job_role" IS '직무 코드 26종. Java enum JobRole';
COMMENT ON COLUMN "project_position"."min_career_years" IS '희망 연차(최소 1)';
COMMENT ON COLUMN "project_position"."headcount" IS '해당 포지션 모집 인원(최소 1)';
COMMENT ON COLUMN "project_position"."confirmed_count" IS '확정 인원';
COMMENT ON COLUMN "project_position"."status" IS 'RECRUITING / FILLED(정원 충족, 매칭 종료) / CLOSED(잔여 모집 종료)';
COMMENT ON COLUMN "project_position"."closed_at" IS '모집 종료 시각';

COMMENT ON COLUMN "position_skill"."id" IS 'PK';
COMMENT ON COLUMN "position_skill"."position_id" IS '포지션 FK';
COMMENT ON COLUMN "position_skill"."skill_code" IS '스킬 코드. Java enum Skill';

COMMENT ON COLUMN "project_file"."id" IS 'PK';
COMMENT ON COLUMN "project_file"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "project_file"."file_id" IS '파일 FK(100MB 이하, 최대 10개)';

COMMENT ON COLUMN "matching_round"."id" IS 'PK';
COMMENT ON COLUMN "matching_round"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "matching_round"."position_id" IS '포지션 FK(재추천은 포지션 단위)';
COMMENT ON COLUMN "matching_round"."round_no" IS '회차(1=최초 추천)';
COMMENT ON COLUMN "matching_round"."round_type" IS 'INITIAL / FREE_RERECOMMEND / PAID_RERECOMMEND';
COMMENT ON COLUMN "matching_round"."requested_count" IS '유료 재추천 시 요청 인원 수';
COMMENT ON COLUMN "matching_round"."cost_amount" IS '재추천 비용(추천 1명당 10,000원)';
COMMENT ON COLUMN "matching_round"."expose_count" IS '노출 후보 수 = 모집 인원 수. 초과 불가. 후보 부족 시 미달 가능';
COMMENT ON COLUMN "matching_round"."embedding_pool_size" IS '1차 임베딩 풀 크기 = 모집 인원 x 3 (재추천은 재추천 인원 x 3)';
COMMENT ON COLUMN "matching_round"."low_score_warned" IS '적합도 임계값 미달 경고 노출 여부';
COMMENT ON COLUMN "matching_round"."status" IS 'RUNNING / COMPLETED / FAILED / EXHAUSTED(후보 소진)';

COMMENT ON COLUMN "matching_candidate"."id" IS 'PK';
COMMENT ON COLUMN "matching_candidate"."round_id" IS '매칭 회차 FK';
COMMENT ON COLUMN "matching_candidate"."position_id" IS '포지션 FK(중복 노출 차단 조회용)';
COMMENT ON COLUMN "matching_candidate"."freelancer_id" IS 'freelancer_profile FK';
COMMENT ON COLUMN "matching_candidate"."stage" IS 'EMBEDDING(1차) / LLM_FINAL(LLM 선정) / GUARD(가드 AI 검증 완료)';
COMMENT ON COLUMN "matching_candidate"."similarity" IS '임베딩 코사인 유사도(0~1)';
COMMENT ON COLUMN "matching_candidate"."base_score" IS 'LLM 원점수(등급 가중치 적용 전, 0~100)';
COMMENT ON COLUMN "matching_candidate"."grade_weight" IS '클라 등급 가중치(%). 등급은 노출 수가 아니라 우선순위·품질에만 적용';
COMMENT ON COLUMN "matching_candidate"."fit_score" IS '최종 적합도 점수(가중치 반영 후, 0~100)';
COMMENT ON COLUMN "matching_candidate"."guard_passed" IS '가드 AI 검증 통과 여부(직무·스킬 재검증)';
COMMENT ON COLUMN "matching_candidate"."guard_reason" IS '가드 AI 탈락 사유';
COMMENT ON COLUMN "matching_candidate"."fit_reason" IS '추천 근거(후보 카드에 표시)';
COMMENT ON COLUMN "matching_candidate"."rank_no" IS '최종 순위';
COMMENT ON COLUMN "matching_candidate"."is_exposed" IS '클라이언트 노출 여부';

COMMENT ON COLUMN "matching_request"."id" IS 'PK';
COMMENT ON COLUMN "matching_request"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "matching_request"."position_id" IS '포지션 FK';
COMMENT ON COLUMN "matching_request"."candidate_id" IS '후보 FK';
COMMENT ON COLUMN "matching_request"."freelancer_id" IS 'freelancer_profile FK';
COMMENT ON COLUMN "matching_request"."status" IS '인원별 상태 11종: REQUEST_PENDING / REJECTED(직접 거절 + 3일 만료) / ACCEPTED / NEGOTIATING / NEGOTIATION_FAILED(협상 결렬) / CONTRACT_PENDING / CONTRACTED / IN_PROGRESS / COMPLETION_PENDING / CLOSED / TERMINATED. 종결 상태는 되돌리지 않음';
COMMENT ON COLUMN "matching_request"."requested_at" IS '요청 발송 시각';
COMMENT ON COLUMN "matching_request"."expires_at" IS '응답 기한(요청 +3일). 초과 시 자동 만료';
COMMENT ON COLUMN "matching_request"."responded_at" IS '응답 시각';
COMMENT ON COLUMN "matching_request"."reject_reason" IS '종료 사유. DIRECT_REJECT / EXPIRED(3일 만료) / NEGOTIATION_FAILED. 무료 재추천 판정에 사용';

COMMENT ON COLUMN "matching_snapshot"."id" IS 'PK';
COMMENT ON COLUMN "matching_snapshot"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "matching_snapshot"."position_id" IS '포지션 FK';
COMMENT ON COLUMN "matching_snapshot"."freelancer_id" IS 'freelancer_profile FK(프리랜서 스냅샷일 때)';
COMMENT ON COLUMN "matching_snapshot"."snapshot_type" IS 'PROJECT / POSITION / FREELANCER';
COMMENT ON COLUMN "matching_snapshot"."snapshot_json" IS '매칭 전환 시점 데이터 동결본. 이후 수정은 미반영';

COMMENT ON COLUMN "negotiation"."id" IS 'PK';
COMMENT ON COLUMN "negotiation"."request_id" IS '수락된 매칭 요청 FK(1:1)';
COMMENT ON COLUMN "negotiation"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "negotiation"."position_id" IS '포지션 FK';
COMMENT ON COLUMN "negotiation"."freelancer_id" IS 'freelancer_profile FK';
COMMENT ON COLUMN "negotiation"."status" IS 'IN_PROGRESS / AI_OUT / AGREED / GIVEN_UP(포기) / BROKEN_DOWN(결렬)';
COMMENT ON COLUMN "negotiation"."total_round" IS '누적 AI 제안 라운드(상한 15)';
COMMENT ON COLUMN "negotiation"."agreed_amount" IS '최종 타결 금액';
COMMENT ON COLUMN "negotiation"."budget_cap" IS '클라 예산 상한(가드)';
COMMENT ON COLUMN "negotiation"."floor_amount" IS '프리 최저 수용가(가드)';
COMMENT ON COLUMN "negotiation"."ai_out_at" IS 'AI Out 시각(사람 채팅 활성화)';
COMMENT ON COLUMN "negotiation"."ended_at" IS '협상 종료 시각';
COMMENT ON COLUMN "negotiation"."end_reason" IS '종료 사유';

COMMENT ON COLUMN "negotiation_condition"."id" IS 'PK';
COMMENT ON COLUMN "negotiation_condition"."negotiation_id" IS '협상 FK';
COMMENT ON COLUMN "negotiation_condition"."condition_type" IS 'AMOUNT / PERIOD / START_DATE / WORK_STYLE / WORK_FORM / CAREER / SKILL';
COMMENT ON COLUMN "negotiation_condition"."client_value" IS '클라이언트 희망값(공개·고정). 초기 제안 카드·상대 희망 힌트용. 마지노선 아님';
COMMENT ON COLUMN "negotiation_condition"."freelancer_value" IS '프리랜서 희망값(공개·고정). 마지노선 아님';
COMMENT ON COLUMN "negotiation_condition"."client_floor" IS '클라이언트 마지노선(비공개). 응답엔 뷰어 본인 것만 myFloor로';
COMMENT ON COLUMN "negotiation_condition"."freelancer_floor" IS '프리랜서 마지노선(비공개). 응답엔 뷰어 본인 것만 myFloor로';
COMMENT ON COLUMN "negotiation_condition"."agreed_value" IS '합의된 값';
COMMENT ON COLUMN "negotiation_condition"."status" IS 'PENDING / IN_PROGRESS / AGREED / FAILED';
COMMENT ON COLUMN "negotiation_condition"."round_count" IS '해당 조건 협상 라운드 수';
COMMENT ON COLUMN "negotiation_condition"."sort_order" IS '협상 순서';

COMMENT ON COLUMN "negotiation_message"."id" IS 'PK';
COMMENT ON COLUMN "negotiation_message"."negotiation_id" IS '협상 FK';
COMMENT ON COLUMN "negotiation_message"."condition_id" IS '협상 조건 FK';
COMMENT ON COLUMN "negotiation_message"."round_no" IS '라운드 번호';
COMMENT ON COLUMN "negotiation_message"."sender_type" IS 'AI_AGENT / CLIENT / FREELANCER / SYSTEM';
COMMENT ON COLUMN "negotiation_message"."message_type" IS 'PROPOSAL / RESPONSE / QUESTION / NOTICE';
COMMENT ON COLUMN "negotiation_message"."content" IS '메시지 본문';
COMMENT ON COLUMN "negotiation_message"."reason" IS '제안 근거. ※AI 제안은 NULL 금지(누락 시 재생성)';
COMMENT ON COLUMN "negotiation_message"."proposed_value" IS '단일 조건 제안 값';
COMMENT ON COLUMN "negotiation_message"."proposal_json" IS '복수 조건 일괄 제안. 2개 이상이면 한 번에 묶어 전달하므로 조건별 값·근거를 배열로 보관';
COMMENT ON COLUMN "negotiation_message"."response" IS 'YES / NO. NO 선택 시에만 직접입력 값을 proposed_value 에 담음';

COMMENT ON COLUMN "negotiation_approval"."id" IS 'PK';
COMMENT ON COLUMN "negotiation_approval"."negotiation_id" IS '협상 FK';
COMMENT ON COLUMN "negotiation_approval"."account_id" IS '응답자 FK';
COMMENT ON COLUMN "negotiation_approval"."party_role" IS 'CLIENT / FREELANCER';
COMMENT ON COLUMN "negotiation_approval"."approved" IS 'Y/N. 양측 Y여야 승인, 한 명이라도 N이면 포기 판정';

COMMENT ON COLUMN "chat_room"."id" IS 'PK';
COMMENT ON COLUMN "chat_room"."negotiation_id" IS '협상 FK(1:1)';
COMMENT ON COLUMN "chat_room"."input_enabled" IS '사람 입력창 활성화. 협상 완료(AI Out) 후 1';
COMMENT ON COLUMN "chat_room"."status" IS 'ACTIVE / CLOSED';

COMMENT ON COLUMN "chat_room_member"."id" IS 'PK';
COMMENT ON COLUMN "chat_room_member"."chat_room_id" IS '채팅방 FK';
COMMENT ON COLUMN "chat_room_member"."account_id" IS '참여자 FK';
COMMENT ON COLUMN "chat_room_member"."party_role" IS 'CLIENT / FREELANCER';
COMMENT ON COLUMN "chat_room_member"."last_read_at" IS '마지막 읽은 시각';
COMMENT ON COLUMN "chat_room_member"."left_at" IS '나가기 시각. 본인 조회에서만 숨김(실제 데이터는 5년 보존)';

COMMENT ON COLUMN "chat_message"."id" IS 'PK';
COMMENT ON COLUMN "chat_message"."chat_room_id" IS '채팅방 FK';
COMMENT ON COLUMN "chat_message"."sender_id" IS '발신자 FK(NULL=시스템)';
COMMENT ON COLUMN "chat_message"."content" IS '메시지(최대 500자)';

COMMENT ON COLUMN "contract"."id" IS 'PK';
COMMENT ON COLUMN "contract"."contract_no" IS '계약 번호';
COMMENT ON COLUMN "contract"."negotiation_id" IS '협상 FK(1:1)';
COMMENT ON COLUMN "contract"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "contract"."position_id" IS '포지션 FK';
COMMENT ON COLUMN "contract"."client_id" IS '클라이언트 FK(갑)';
COMMENT ON COLUMN "contract"."freelancer_id" IS '프리랜서 FK(을)';
COMMENT ON COLUMN "contract"."total_amount" IS '총 계약 금액. 부가세 별도(표기만 하며 별도 계산하지 않음)';
COMMENT ON COLUMN "contract"."down_amount" IS '착수금';
COMMENT ON COLUMN "contract"."final_amount" IS '성공보수(잔금)';
COMMENT ON COLUMN "contract"."start_date" IS '착수일';
COMMENT ON COLUMN "contract"."end_date" IS '완료일';
COMMENT ON COLUMN "contract"."work_style" IS '근무 방식';
COMMENT ON COLUMN "contract"."work_form" IS '근무 형태';
COMMENT ON COLUMN "contract"."work_location" IS '근무 장소(상주 시)';
COMMENT ON COLUMN "contract"."inspection_days" IS '제8조 검수 기한(일)';
COMMENT ON COLUMN "contract"."payment_days" IS '제5조 검수 후 지급 기한(일)';
COMMENT ON COLUMN "contract"."confidential_years" IS '제10조 비밀유지 존속 연수';
COMMENT ON COLUMN "contract"."penalty_rate" IS '제12조 위약금율(%). 상대방 10% + 플랫폼 10%';
COMMENT ON COLUMN "contract"."special_terms" IS '제15조 특약사항(협상 로그 기반)';
COMMENT ON COLUMN "contract"."content_json" IS '계약서 조항 스냅샷(렌더링용)';
COMMENT ON COLUMN "contract"."pdf_file_id" IS '생성된 계약서 PDF FK(S3)';
COMMENT ON COLUMN "contract"."esign_provider" IS '외부 전자서명 서비스명';
COMMENT ON COLUMN "contract"."esign_doc_id" IS '외부 서비스 문서 ID';
COMMENT ON COLUMN "contract"."status" IS 'DRAFT / PENDING_SIGN / SIGNED / IN_PROGRESS / COMPLETION_PENDING / CLOSED / TERMINATED';
COMMENT ON COLUMN "contract"."signed_at" IS '양측 서명 완료 시각(계약 체결)';
COMMENT ON COLUMN "contract"."completed_at" IS '검수 완료 시각';
COMMENT ON COLUMN "contract"."terminated_at" IS '중도 해지 시각';
COMMENT ON COLUMN "contract"."terminated_by" IS '파기 주체 CLIENT / FREELANCER';
COMMENT ON COLUMN "contract"."retention_until" IS '보존 만료일(정산 완료 +5년)';

COMMENT ON COLUMN "contract_signature"."id" IS 'PK';
COMMENT ON COLUMN "contract_signature"."contract_id" IS '계약 FK';
COMMENT ON COLUMN "contract_signature"."account_id" IS '서명자 FK';
COMMENT ON COLUMN "contract_signature"."party_role" IS 'CLIENT(갑) / FREELANCER(을)';
COMMENT ON COLUMN "contract_signature"."status" IS 'PENDING / SIGNED / REJECTED';
COMMENT ON COLUMN "contract_signature"."verification_method" IS 'EMAIL / PHONE / CERT(인증서). 증거력 수준 기록';
COMMENT ON COLUMN "contract_signature"."signed_at" IS '서명 시각';
COMMENT ON COLUMN "contract_signature"."ip_address" IS '서명 시점 IP';
COMMENT ON COLUMN "contract_signature"."user_agent" IS '서명 시점 User-Agent';
COMMENT ON COLUMN "contract_signature"."timestamp_token" IS '타임스탬프 토큰(제3자 시각 보증)';
COMMENT ON COLUMN "contract_signature"."signature_file_id" IS '서명 이미지 FK(자체 구현 시)';
COMMENT ON COLUMN "contract_signature"."provider_signer_id" IS '외부 서명 서비스 참여자 ID';
COMMENT ON COLUMN "contract_signature"."reject_reason" IS '서명 거절 사유';

COMMENT ON COLUMN "virtual_account"."id" IS 'PK';
COMMENT ON COLUMN "virtual_account"."account_id" IS '계정 FK(1:1)';
COMMENT ON COLUMN "virtual_account"."balance" IS '현재 잔액(원). 실제 PG 미연동, 값 증감만 처리';
COMMENT ON COLUMN "virtual_account"."total_charged" IS '누적 충전액';
COMMENT ON COLUMN "virtual_account"."total_withdrawn" IS '누적 차감액';

COMMENT ON COLUMN "ledger_entry"."id" IS 'PK';
COMMENT ON COLUMN "ledger_entry"."entry_no" IS '거래 번호';
COMMENT ON COLUMN "ledger_entry"."virtual_account_id" IS '가상 계좌 FK';
COMMENT ON COLUMN "ledger_entry"."direction" IS 'CREDIT(입금·증가) / DEBIT(출금·차감)';
COMMENT ON COLUMN "ledger_entry"."entry_type" IS 'CHARGE(충전) / SETTLEMENT(수수료) / PENALTY(위약금) / RERECOMMEND(유료 재추천)';
COMMENT ON COLUMN "ledger_entry"."amount" IS '거래 금액(원)';
COMMENT ON COLUMN "ledger_entry"."balance_after" IS '거래 후 잔액(감사 추적용)';
COMMENT ON COLUMN "ledger_entry"."ref_type" IS 'SETTLEMENT / PENALTY / REROLL_PURCHASE';
COMMENT ON COLUMN "ledger_entry"."ref_id" IS '연관 리소스 ID';
COMMENT ON COLUMN "ledger_entry"."memo" IS '적요';
COMMENT ON COLUMN "ledger_entry"."status" IS 'COMPLETED / FAILED(잔액 부족) / CANCELED';
COMMENT ON COLUMN "ledger_entry"."fail_reason" IS '실패 사유(잔액 부족 등)';

COMMENT ON COLUMN "settlement"."id" IS 'PK';
COMMENT ON COLUMN "settlement"."settlement_no" IS '정산 번호(관리자 검색용)';
COMMENT ON COLUMN "settlement"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "settlement"."contract_id" IS '계약 FK(클라 착수금은 계약 전이라 NULL 가능)';
COMMENT ON COLUMN "settlement"."payer_account_id" IS '수수료 납부자 FK';
COMMENT ON COLUMN "settlement"."payer_role" IS 'CLIENT / FREELANCER';
COMMENT ON COLUMN "settlement"."phase" IS 'DEPOSIT(착수금) / SUCCESS_FEE(성공보수)';
COMMENT ON COLUMN "settlement"."base_amount" IS '수수료 산정 기준 금액';
COMMENT ON COLUMN "settlement"."fee_rate" IS '적용 요율(%). 등급 인하 반영 후';
COMMENT ON COLUMN "settlement"."grade_discount" IS '등급 인하율(%)';
COMMENT ON COLUMN "settlement"."fee_amount" IS '수수료 금액(원)';
COMMENT ON COLUMN "settlement"."ledger_entry_id" IS '원장 거래 FK';
COMMENT ON COLUMN "settlement"."status" IS 'PENDING(버튼 비활성) / PAYABLE(활성) / PAID / OVERDUE(미납) / FAILED';
COMMENT ON COLUMN "settlement"."due_date" IS '납부 기한(미납 시 탈퇴 제한)';
COMMENT ON COLUMN "settlement"."paid_at" IS '결제 완료 시각';

COMMENT ON COLUMN "penalty"."id" IS 'PK';
COMMENT ON COLUMN "penalty"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "penalty"."contract_id" IS '계약 FK. 모집 기간 초과 후 클라 파기는 계약 전이라 NULL';
COMMENT ON COLUMN "penalty"."terminator_role" IS '파기 주체 CLIENT / FREELANCER';
COMMENT ON COLUMN "penalty"."payee_type" IS '수령 대상 COUNTERPARTY(상대방) / PLATFORM(페어링)';
COMMENT ON COLUMN "penalty"."worked_amount" IS '수행분 정산 기준 금액(계약 전 파기 시 0)';
COMMENT ON COLUMN "penalty"."penalty_rate" IS '위약금율(%). 상대방 10 + 플랫폼 10';
COMMENT ON COLUMN "penalty"."penalty_amount" IS '위약금 금액(원)';
COMMENT ON COLUMN "penalty"."payer_account_id" IS '위약금 지급자 FK';
COMMENT ON COLUMN "penalty"."payee_account_id" IS '위약금 수령자 FK(플랫폼이면 NULL)';
COMMENT ON COLUMN "penalty"."ledger_entry_id" IS '원장 거래 FK';
COMMENT ON COLUMN "penalty"."status" IS 'PENDING / PAYABLE / PAID / OVERDUE';

COMMENT ON COLUMN "rerecommend_purchase"."id" IS 'PK';
COMMENT ON COLUMN "rerecommend_purchase"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "rerecommend_purchase"."round_id" IS '매칭 회차 FK';
COMMENT ON COLUMN "rerecommend_purchase"."ledger_entry_id" IS '원장 거래 FK';
COMMENT ON COLUMN "rerecommend_purchase"."unit_price" IS '추천 1명당 단가(원)';
COMMENT ON COLUMN "rerecommend_purchase"."quantity" IS '추천 요청 인원 수';
COMMENT ON COLUMN "rerecommend_purchase"."amount" IS '총 결제 금액';
COMMENT ON COLUMN "rerecommend_purchase"."status" IS 'PENDING / PAID / FAILED';

COMMENT ON COLUMN "review"."contract_id" IS '계약 FK';
COMMENT ON COLUMN "review"."project_id" IS '프로젝트 FK(조회 편의)';
COMMENT ON COLUMN "review"."content" IS '리뷰(선택, 500자 이하)';
COMMENT ON COLUMN "review"."created_at" IS '작성 후 수정·삭제 불가. 탈퇴해도 삭제 안 함';

COMMENT ON COLUMN "site_review"."id" IS 'PK';
COMMENT ON COLUMN "site_review"."contract_id" IS '계약 FK';
COMMENT ON COLUMN "site_review"."project_id" IS '프로젝트 FK';
COMMENT ON COLUMN "site_review"."writer_role" IS 'CLIENT / FREELANCER';
COMMENT ON COLUMN "site_review"."content" IS '이용후기(선택, 500자 이하)';

COMMENT ON COLUMN "notification"."id" IS 'PK';
COMMENT ON COLUMN "notification"."type" IS 'RECOMMEND_DONE / REQUEST_RECEIVED / REQUEST_ACCEPTED / REQUEST_REJECTED / NEGOTIATION_START / AI_PROPOSAL / NEGOTIATION_BROKEN / CONTRACT_CREATED / CONTRACT_SIGNED / CONTRACT_REJECTED / PROJECT_STATUS_CHANGED / SETTLEMENT_DUE';
COMMENT ON COLUMN "notification"."title" IS '알림 제목';
COMMENT ON COLUMN "notification"."content" IS '알림 내용';
COMMENT ON COLUMN "notification"."link_url" IS '클릭 시 이동 경로';
COMMENT ON COLUMN "notification"."read_at" IS '읽은 시각';

COMMENT ON COLUMN "chatbot_session"."id" IS 'PK';
COMMENT ON COLUMN "chatbot_session"."owner_account_id" IS '사용자 FK';

COMMENT ON COLUMN "chatbot_message"."id" IS 'PK';
COMMENT ON COLUMN "chatbot_message"."session_id" IS '세션 FK';
COMMENT ON COLUMN "chatbot_message"."question" IS '사용자 질문';
COMMENT ON COLUMN "chatbot_message"."answer" IS '챗봇 답변';
COMMENT ON COLUMN "chatbot_message"."intent" IS '답변에 딸린 이동 버튼의 화면 코드(ChatbotIntent). 없으면 NONE 또는 NULL';

COMMENT ON COLUMN "chatbot_quota"."id" IS 'PK';
COMMENT ON COLUMN "chatbot_quota"."account_id" IS '사용자 FK';
COMMENT ON COLUMN "chatbot_quota"."quota_date" IS '기준 일자';
COMMENT ON COLUMN "chatbot_quota"."used_count" IS 'LLM 호출 사용 횟수(1일 10회 충전)';

COMMENT ON COLUMN "inquiry"."id" IS 'PK';
COMMENT ON COLUMN "inquiry"."title" IS '제목';
COMMENT ON COLUMN "inquiry"."content" IS '내용';
COMMENT ON COLUMN "inquiry"."status" IS 'PENDING(대기중) / ANSWERED(답변완료). 관리자가 답변을 작성하면 ANSWERED 로 전환하고 사용자에게 알림을 발송한다';
COMMENT ON COLUMN "inquiry"."answer" IS '답변';

COMMENT ON COLUMN "ai_agent_log"."id" IS 'PK';
COMMENT ON COLUMN "ai_agent_log"."agent_type" IS 'PARSER / EMBEDDING / MATCHER / GUARD(직무·스킬 재검증) / NEGOTIATOR / CONTRACT / CHATBOT';
COMMENT ON COLUMN "ai_agent_log"."ref_type" IS '연관 도메인';
COMMENT ON COLUMN "ai_agent_log"."ref_id" IS '연관 리소스 ID';
COMMENT ON COLUMN "ai_agent_log"."model" IS '사용 모델명';
COMMENT ON COLUMN "ai_agent_log"."request_json" IS '요청 페이로드';
COMMENT ON COLUMN "ai_agent_log"."response_json" IS '응답 페이로드';
COMMENT ON COLUMN "ai_agent_log"."prompt_tokens" IS '입력 토큰 수';
COMMENT ON COLUMN "ai_agent_log"."output_tokens" IS '출력 토큰 수';
COMMENT ON COLUMN "ai_agent_log"."cost_amount" IS '호출 비용';
COMMENT ON COLUMN "ai_agent_log"."latency_ms" IS '응답 소요 시간(ms)';
COMMENT ON COLUMN "ai_agent_log"."retry_count" IS '재시도 횟수. LLM 오류 시 3회까지 재시도하고 초과하면 실패 표시';
COMMENT ON COLUMN "ai_agent_log"."status" IS 'SUCCESS / FAILED';
COMMENT ON COLUMN "ai_agent_log"."error_message" IS '오류 메시지';

COMMENT ON COLUMN "chat_message"."message_type" IS 'TEXT(일반 메시지) / SYSTEM(시스템 안내) / FILE';
COMMENT ON COLUMN "chat_message"."payload" IS '부가 데이터(첨부 파일 정보, 시스템 메시지 파라미터 등). 정형화되지 않는 값만 담는다';


-- =====================================================================
-- 7. updated_at 자동 갱신 트리거
--    MySQL 의 ON UPDATE CURRENT_TIMESTAMP 를 대체한다.
-- =====================================================================

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER "trg_file_updated_at" BEFORE UPDATE ON "file"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_terms_updated_at" BEFORE UPDATE ON "terms"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_account_updated_at" BEFORE UPDATE ON "account"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_social_account_updated_at" BEFORE UPDATE ON "social_account"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_client_profile_updated_at" BEFORE UPDATE ON "client_profile"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_freelancer_profile_updated_at" BEFORE UPDATE ON "freelancer_profile"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_payment_method_updated_at" BEFORE UPDATE ON "payment_method"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_terms_agreement_updated_at" BEFORE UPDATE ON "terms_agreement"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_email_verification_updated_at" BEFORE UPDATE ON "email_verification"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_freelancer_condition_updated_at" BEFORE UPDATE ON "freelancer_condition"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_condition_skill_updated_at" BEFORE UPDATE ON "condition_skill"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_resume_updated_at" BEFORE UPDATE ON "resume"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_resume_education_updated_at" BEFORE UPDATE ON "resume_education"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_resume_career_updated_at" BEFORE UPDATE ON "resume_career"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_resume_certificate_updated_at" BEFORE UPDATE ON "resume_certificate"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_resume_link_updated_at" BEFORE UPDATE ON "resume_link"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_project_updated_at" BEFORE UPDATE ON "project"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_project_position_updated_at" BEFORE UPDATE ON "project_position"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_position_skill_updated_at" BEFORE UPDATE ON "position_skill"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_project_file_updated_at" BEFORE UPDATE ON "project_file"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_matching_round_updated_at" BEFORE UPDATE ON "matching_round"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_matching_candidate_updated_at" BEFORE UPDATE ON "matching_candidate"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_matching_request_updated_at" BEFORE UPDATE ON "matching_request"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_negotiation_updated_at" BEFORE UPDATE ON "negotiation"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_negotiation_condition_updated_at" BEFORE UPDATE ON "negotiation_condition"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_chat_room_updated_at" BEFORE UPDATE ON "chat_room"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_chat_room_member_updated_at" BEFORE UPDATE ON "chat_room_member"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_contract_updated_at" BEFORE UPDATE ON "contract"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_contract_signature_updated_at" BEFORE UPDATE ON "contract_signature"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_virtual_account_updated_at" BEFORE UPDATE ON "virtual_account"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_settlement_updated_at" BEFORE UPDATE ON "settlement"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_penalty_updated_at" BEFORE UPDATE ON "penalty"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER "trg_rerecommend_purchase_updated_at" BEFORE UPDATE ON "rerecommend_purchase"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- chatbot_session 은 updated_at 이 없다(한 번 만들고 바뀌지 않는다). 트리거를 두지 않는다.
CREATE TRIGGER "trg_chatbot_quota_updated_at" BEFORE UPDATE ON "chatbot_quota"
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
