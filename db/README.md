# DB 초기화

PostgreSQL 스키마 관리 파일입니다. Flyway/Liquibase는 아직 도입하지 않았고, 이 스크립트를 손으로 실행합니다.

## 실행 순서

```bash
psql -U postgres -f db/init/01-create-pairing-account.sql
```

```bash
psql -U pairing -d pairing -v ON_ERROR_STOP=1 -f db/init/02-create-schema.sql
```

```bash
psql -U pairing -d pairing -v ON_ERROR_STOP=1 -f db/init/99-create-example-table.sql
```

| 파일 | 역할 |
| --- | --- |
| `init/01-create-pairing-account.sql` | pairing 역할·데이터베이스 생성, public 스키마 권한 |
| `init/02-create-schema.sql` | 테이블 46개 + 제약·인덱스·주석·트리거 (스키마 v12) |
| `init/99-create-example-table.sql` | 레퍼런스 도메인 `examples` 테이블. 레퍼런스 도메인을 지우면 이 파일도 지운다 |

`02`는 DROP을 하지 않습니다. 다시 만들려면 pairing 데이터베이스에 접속해 아래를 먼저 실행합니다.

```bash
psql -U pairing -d pairing -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO pairing;"
```

## ddl-auto = validate

`src/main/resources/application.yaml`의 `spring.jpa.hibernate.ddl-auto`는 `validate`입니다. Hibernate가 스키마를 바꾸지 않고, 엔티티와 테이블이 어긋나면 애플리케이션이 시작에 실패합니다.

- 스키마 변경은 항상 `db/init/*.sql`을 고치고 DB에 적용한 뒤 엔티티를 맞춥니다. 반대 방향(엔티티 먼저)으로 하면 시작이 막힙니다.
- 새 엔티티를 매핑하면 해당 테이블이 DB에 있어야 합니다.
- `application-local.yaml`은 H2 + `create-drop`이라 영향받지 않습니다. 테스트도 H2 + `create-drop`입니다.

## 스키마 구성

기획 문서는 [docs/spec/](../docs/spec/README.md)를 참고합니다. 상태값 enum은 컬럼 주석(`COMMENT ON COLUMN`)에 정의되어 있으므로 Java enum을 만들 때 주석을 기준으로 합니다.

| 도메인 | 테이블 |
| --- | --- |
| 공통 | `file`, `terms` |
| 계정·인증 | `account`, `social_account`, `terms_agreement`, `email_verification`, `payment_method` |
| 프로필 | `client_profile`, `freelancer_profile` |
| 프리랜서 등록 | `freelancer_condition`, `condition_skill`, `resume`, `resume_education`, `resume_career`, `resume_certificate`, `resume_link` |
| 프로젝트 | `project`, `project_position`, `position_skill`, `project_file` |
| 매칭 | `matching_round`, `matching_candidate`, `matching_request`, `matching_snapshot` |
| 협상 | `negotiation`, `negotiation_condition`, `negotiation_message`, `negotiation_approval` |
| 채팅 | `chat_room`, `chat_room_member`, `chat_message` |
| 계약 | `contract`, `contract_signature` |
| 정산 | `virtual_account`, `ledger_entry`, `settlement`, `penalty`, `rerecommend_purchase` |
| 리뷰 | `review`, `site_review` |
| 알림·문의 | `notification`, `chatbot_session`, `chatbot_message`, `chatbot_quota`, `inquiry` |
| 레퍼런스 | `examples` (99번 파일. 운영 도메인 아님) |
| 운영 | `ai_agent_log` |

### 설계 규칙

- PK는 `BIGINT GENERATED ALWAYS AS IDENTITY`
- 코드성 값은 `VARCHAR` + 컬럼 주석. Java에서 `@Enumerated(EnumType.STRING)`으로 매핑
- 금액 `NUMERIC(15,0)` 원 단위 / 비율 `NUMERIC(5,2)` 퍼센트
- 시각은 `TIMESTAMP`에 UTC 저장, 애플리케이션에서 KST 변환
- 비정형 데이터는 `JSONB`
- `updated_at`은 `set_updated_at()` 트리거로 자동 갱신 (MySQL `ON UPDATE` 대체)
- FK는 전부 `NO ACTION`. `account` 행은 물리 삭제하지 않고 탈퇴 시 개인정보만 마스킹

### DB가 아니라 Redis에서 관리하는 것

JWT Refresh Token(Access 30분 / Refresh 7일), 회원 정지 상태, 비밀번호 초기화 링크 토큰, 이메일 발송 제한(1시간 15회), 로그인 IP 차단(1시간 20회 → 2시간).

### application.yml 상수로 빼야 하는 값

스키마에 하드코딩하지 않고 설정으로 관리합니다.

```
rerecommend: free-max=1, paid-max=5, unit-price=10000
recruit: weeks=2, extension-max=2
negotiation: round-max=15
matching: request-expire-days=3
chatbot: daily-quota=10
fee: 1억 미만 착수금 클라 3%/프리 4%, 성공보수 클라 7%/프리 6%
     1억 이상 착수금 클라 2%/프리 4%, 성공보수 클라 6%/프리 6%
grade: 골드 3.0·10건 / 다이아 4.0·20건 / 시니어 3.0·5건 / 마스터 4.0·10건
```

### 챗봇 / 1:1 문의

두 창구는 서로 독립이며 사용자가 선택합니다. 챗봇으로 해결이 안 됐을 때만 문의가 열리는 구조가 아닙니다.

- `inquiry`에는 챗봇 세션 FK가 없습니다. 문의는 언제든 단독 접수됩니다.
- `inquiry.status`는 `PENDING` / `ANSWERED` 2종입니다.
- `chatbot_session.status`는 `ACTIVE` / `CLOSED`이며, 문의 전환 상태를 두지 않습니다.
- 챗봇 LLM 호출은 `chatbot_quota`로 하루 10회 제한합니다. 문의에는 한도가 없습니다.

### 스키마에서 제외된 것

- `freelancer_embedding`, `position_embedding` → FastAPI + ChromaDB로 이관
- `project_parse` → AI 검수 결과는 FastAPI 측 처리
- `deliverable` → 산출물 제출·검수 기능 제외
