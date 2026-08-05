-- =====================================================================
-- 약관 시드 데이터  [PostgreSQL]
--
-- 선행: db/init/02-create-schema.sql
--
-- 실행 (psql):
--   psql -U pairing -d pairing -v ON_ERROR_STOP=1 -f db/init/03-seed-terms.sql
--
-- 약관 행이 하나도 없으면 회원가입이 불가능하다.
-- 가입 API가 "노출 중인 약관 목록"과 요청의 약관 ID를 대조하기 때문이다. (TM_003)
--
-- 규칙
--   - terms 는 버전별 이력 테이블이다. 내용이 바뀌면 UPDATE 하지 말고 새 version 으로 INSERT 한다.
--   - target_role 이 NULL 이면 공통 약관, CLIENT/FREELANCER 면 해당 역할에게만 노출된다.
--   - is_required = TRUE 인 약관은 전부 동의해야 가입이 완료된다.
--   - content 는 실제 약관 전문으로 교체해야 한다. 아래는 자리표시자다.
-- =====================================================================

INSERT INTO "terms" ("code", "version", "title", "content", "is_required", "target_role", "effective_at")
VALUES
    ('SERVICE_CLIENT', 'v1.0', '클라이언트 서비스 이용약관',
     '[자리표시자] 클라이언트 서비스 이용약관 전문을 넣는다.',
     TRUE, 'CLIENT', CURRENT_TIMESTAMP),

    ('SERVICE_FREELANCER', 'v1.0', '프리랜서 서비스 이용약관',
     '[자리표시자] 프리랜서 서비스 이용약관 전문을 넣는다.',
     TRUE, 'FREELANCER', CURRENT_TIMESTAMP),

    ('PRIVACY', 'v1.0', '개인정보 수집·이용 동의',
     '[자리표시자] 수집 항목과 보유 기간을 넣는다. (정책 미확정 항목)',
     TRUE, NULL, CURRENT_TIMESTAMP),

    ('FEE_NOTICE', 'v1.0', '수수료 안내 동의',
     '[자리표시자] 착수금·성공보수 수수료율 안내를 넣는다.',
     TRUE, NULL, CURRENT_TIMESTAMP),

    ('REVIEW_EXPOSURE', 'v1.0', '리뷰 노출 동의',
     '[자리표시자] 작성한 리뷰가 메인페이지·홍보에 사용될 수 있다는 안내를 넣는다.',
     TRUE, NULL, CURRENT_TIMESTAMP),

    ('MARKETING', 'v1.0', '마케팅 정보 수신 동의',
     '[자리표시자] 선택 동의 항목이다.',
     FALSE, NULL, CURRENT_TIMESTAMP);


-- 확인
SELECT "id", "code", "version", "is_required", "target_role"
FROM "terms"
ORDER BY "target_role" NULLS FIRST, "code";
