-- =====================================================================
-- pairing 역할(계정) · 데이터베이스 생성  [PostgreSQL]
--
-- 대상: 로컬에 설치된 PostgreSQL 15 이상 (확인 환경: PostgreSQL 18)
--
-- 실행 (psql):
--   psql -U postgres -f db/init/01-create-pairing-account.sql
--
-- 실행 (pgAdmin):
--   postgres 데이터베이스에 접속해 "1. 역할" ~ "2. 데이터베이스" 를 실행한 뒤,
--   pairing 데이터베이스로 접속을 바꿔서 "3. 스키마 권한" 을 실행한다.
--   (\connect 는 psql 전용 명령이라 pgAdmin에서는 동작하지 않는다)
--
-- PostgreSQL은 인코딩을 데이터베이스 단위로 지정한다. UTF8로 만들면 한글·이모지가 모두 저장된다.
-- MySQL의 utf8mb4에 해당하는 별도 설정은 필요 없다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. 역할(계정) 생성
--    PostgreSQL은 사용자와 그룹을 모두 ROLE로 다룬다. LOGIN 속성이 있으면 접속 계정이다.
--    CREATE ROLE 에는 IF NOT EXISTS 가 없으므로 DO 블록으로 분기한다.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'pairing') THEN
        CREATE ROLE pairing WITH LOGIN PASSWORD 'pairing';
    ELSE
        -- 이미 있으면 비밀번호와 로그인 속성만 맞춘다.
        ALTER ROLE pairing WITH LOGIN PASSWORD 'pairing';
    END IF;
END
$$;


-- ---------------------------------------------------------------------
-- 2. 데이터베이스 생성
--    OWNER를 pairing으로 지정하는 것이 중요하다.
--    PostgreSQL 15부터 public 스키마의 CREATE 권한이 PUBLIC에서 회수되었기 때문에,
--    소유자가 아니면 ddl-auto=update 가 테이블을 만들 수 없다.
--    데이터베이스 소유자는 pg_database_owner 역할에 암시적으로 속하므로 public 스키마에 생성이 가능하다.
--
--    CREATE DATABASE 는 트랜잭션 안에서 실행할 수 없어 DO 블록을 쓸 수 없다.
--    이미 존재하면 "database already exists" 오류가 나며, 무시해도 된다.
-- ---------------------------------------------------------------------
CREATE DATABASE pairing
    WITH OWNER    = pairing
         ENCODING = 'UTF8'
         TEMPLATE = template0;

-- 이미 데이터베이스가 있고 소유자만 바꾸려면 위 문장 대신 아래를 실행한다.
-- ALTER DATABASE pairing OWNER TO pairing;


-- ---------------------------------------------------------------------
-- 3. 스키마 권한
--    아래부터는 pairing 데이터베이스에 접속한 상태에서 실행해야 한다.
--    소유자로 지정했으면 대부분 이미 권한이 있지만, 명시해두면 소유자가 아닌 경우에도 동작한다.
-- ---------------------------------------------------------------------
\connect pairing

GRANT ALL ON SCHEMA public TO pairing;
ALTER SCHEMA public OWNER TO pairing;

-- 앞으로 다른 역할이 만드는 객체에도 pairing이 접근할 수 있게 기본 권한을 설정한다. (선택)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO pairing;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO pairing;


-- ---------------------------------------------------------------------
-- 4. 확인
-- ---------------------------------------------------------------------
-- 데이터베이스 인코딩·소유자 확인 (encoding 이 UTF8, owner 가 pairing 이어야 한다)
SELECT d.datname                        AS db_name,
       pg_encoding_to_char(d.encoding)  AS encoding,
       pg_get_userbyid(d.datdba)        AS owner,
       d.datcollate                     AS collate
FROM pg_database d
WHERE d.datname = 'pairing';

-- 역할 확인
SELECT rolname, rolcanlogin, rolsuper
FROM pg_roles
WHERE rolname = 'pairing';

-- public 스키마에 테이블을 만들 권한이 있는지 확인 (t 가 나와야 한다)
SELECT has_schema_privilege('pairing', 'public', 'CREATE') AS can_create_table;
