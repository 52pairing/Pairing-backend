-- email_verification.purpose 에 PAYMENT_METHOD 를 허용한다. (결제수단 탭 이메일 인증)
--
-- ■ 왜 필요한가
--   purpose 는 @Enumerated(STRING) 이라 Hibernate 가 테이블을 "생성"할 때 CHECK 제약을 함께 굽는다.
--   그런데 운영 설정은 ddl-auto: update 라서 컬럼은 추가해 주지만 기존 제약은 갱신하지 않는다.
--   enum 값만 늘리고 배포하면 인증코드 발송이 INSERT 단계에서 거부되고 트랜잭션이 통째로 롤백된다.
--   (알림 도메인에 PROJECT_CANCELED 를 넣을 때 같은 일을 겪었다)
--
-- ■ 어디에 실행하는가
--   Hibernate 가 만든 테이블을 쓰는 DB 전부(로컬·개발·운영). 배포보다 먼저 실행한다.
--   db/init/02-create-schema.sql 로 처음부터 만든 DB 에는 이 CHECK 자체가 없어서 실행할 필요가 없다.
--   (아래 DROP 이 실패해도 무방하다는 뜻이다)
--   테스트는 ddl-auto: create-drop 이라 Hibernate 가 다섯 값으로 다시 구워 준다.
--
-- ■ 안전한가
--   허용 목록을 넓히기만 한다. 기존 행과 기능에는 영향이 없다.
--
-- 제약 이름은 Hibernate 가 자동 생성한 것이라 환경에 따라 다를 수 있다.
-- 이름이 다르면 아래로 실제 이름을 먼저 확인한다.
--   SELECT conname FROM pg_constraint
--    WHERE conrelid = 'email_verification'::regclass AND contype = 'c';

ALTER TABLE "email_verification"
    DROP CONSTRAINT IF EXISTS "email_verification_purpose_check";

ALTER TABLE "email_verification"
    ADD CONSTRAINT "email_verification_purpose_check"
    CHECK ("purpose" IN ('SIGNUP', 'UNLOCK', 'PROFILE_UPDATE', 'PASSWORD_CHANGE', 'PAYMENT_METHOD'));
