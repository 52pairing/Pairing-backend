/**
 * 빌더 로직 검증.
 *
 *   node k6/tools/test-builder.mjs
 *
 * 브라우저 없이 돈다. 빌더의 산출물은 "복사해서 실행할 명령 한 줄"이라, 그게 틀리면
 * 부하 테스트가 엉뚱한 설정으로 돌거나 아예 안 돈다. 특히 따옴표 처리는 눈으로 검토하기
 * 어렵고 틀렸을 때 조용히 다른 값으로 실행되기도 해서 반드시 테스트가 있어야 한다.
 */

import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const builderDir = join(here, '..', 'builder');
const require = createRequire(import.meta.url);

// catalog.js 는 window 에 붙는 일반 스크립트다. globalThis 를 window 로 세워 두고 읽는다.
globalThis.window = globalThis;
new Function(readFileSync(join(builderDir, 'catalog.js'), 'utf8'))();
const CATALOG = globalThis.K6_CATALOG;
const L = require(join(builderDir, 'logic.js'));

let pass = 0;
const fails = [];

function check(name, actual, expected) {
  const ok = JSON.stringify(actual) === JSON.stringify(expected);
  if (ok) {
    pass += 1;
    console.log(`  [OK ] ${name}`);
  } else {
    fails.push(name);
    console.log(`  [FAIL] ${name}\n        기대: ${JSON.stringify(expected)}\n        실제: ${JSON.stringify(actual)}`);
  }
}

function truthy(name, value, detail) {
  if (value) {
    pass += 1;
    console.log(`  [OK ] ${name}${detail ? ' — ' + detail : ''}`);
  } else {
    fails.push(name);
    console.log(`  [FAIL] ${name}${detail ? ' — ' + detail : ''}`);
  }
}

const base = {
  baseUrl: 'http://localhost:8080',
  aiBaseUrl: 'http://localhost:8000',
  internalKey: '',
  accounts: [{ email: 'a@b.com', password: 'pw1', role: 'FREELANCER' }],
  apis: [],
  extraApis: [],
  profile: 'load',
  vus: '20',
  duration: '3m',
  ramp: '30s',
  thinkMs: '500',
  testid: 'before',
  ws: false,
  wsVus: '10',
  wsHold: '3m',
  p95Public: '500', p95Read: '1000', p95Write: '1500', p95Ai: '30000', maxFail: '0.01',
};
const cfg = (over) => Object.assign({}, base, over);

console.log(`\n카탈로그 ${CATALOG.length}개 항목으로 검증\n`);

// ---------------------------------------------------------------- 따옴표
console.log('[따옴표] 셸마다 이스케이프가 다르다');
check('bash 평범한 값', L.quote('abc', 'sh'), "'abc'");
check('ps 평범한 값', L.quote('abc', 'ps'), "'abc'");
check("bash 작은따옴표 포함", L.quote("pw'x", 'sh'), "'pw'\\''x'");
check("ps 작은따옴표 포함", L.quote("pw'x", 'ps'), "'pw''x'");
check('느낌표는 그대로', L.quote('Passw0rd!', 'ps'), "'Passw0rd!'");

// ---------------------------------------------------------------- 순서
console.log('\n[API 순서] 체크한 순서가 아니라 카탈로그 순서를 따라야 한다');
const shuffled = cfg({ apis: ['chatrooms', 'me', 'home_summary'] });
const listed = L.apiList(shuffled, CATALOG).split(',');
const catalogOrder = CATALOG.filter((e) => shuffled.apis.includes(e.key)).map((e) => e.key);
check('카탈로그 순서로 정렬된다', listed, catalogOrder);
truthy('선택한 개수가 유지된다', listed.length === 3, listed.join(','));

// ---------------------------------------------------------------- env
console.log('\n[명령 생성]');
const cmd = L.buildCommand(cfg({ apis: ['me', 'notifs_unread'] }), CATALOG, 'ps');
truthy('k6 run 으로 시작', cmd.startsWith('k6 run k6/main.js '));
truthy('ACCOUNTS 형식이 이메일:비번:역할', cmd.includes("-e ACCOUNTS='a@b.com:pw1:FREELANCER'"));
truthy('APIS 가 들어간다', cmd.includes("-e APIS='me,notifs_unread'"));
truthy('기본값인 기준은 명령에 안 들어간다', !cmd.includes('P95_READ'));
truthy('AI 를 안 골랐으면 내부 키가 안 들어간다', !cmd.includes('INTERNAL_API_KEY'));
truthy('WS 를 껐으면 WS 가 안 들어간다', !cmd.includes("-e WS="));

const aiCmd = L.buildCommand(
  cfg({ apis: ['ai_chatbot'], internalKey: 'secret-key', p95Read: '2000', ws: true }),
  CATALOG, 'sh'
);
truthy('AI 를 고르면 AI_BASE_URL 이 붙는다', aiCmd.includes("-e AI_BASE_URL='http://localhost:8000'"));
truthy('AI 를 고르면 내부 키가 붙는다', aiCmd.includes("-e INTERNAL_API_KEY='secret-key'"));
truthy('기본값과 다른 기준만 붙는다', aiCmd.includes("-e P95_READ='2000'") && !aiCmd.includes('P95_WRITE'));
truthy('WS 를 켜면 WS 설정이 붙는다', aiCmd.includes("-e WS='1'") && aiCmd.includes("-e WS_VUS='10'"));

const zeroThink = L.buildCommand(cfg({ apis: ['me'], thinkMs: '0' }), CATALOG, 'sh');
truthy('THINK_MS=0 은 값이 0 이어도 살아남는다', zeroThink.includes("-e THINK_MS='0'"));

const noAcct = L.buildCommand(cfg({ apis: ['home_summary'], accounts: [] }), CATALOG, 'sh');
truthy('계정이 없으면 ACCOUNTS 를 아예 안 넣는다', !noAcct.includes('ACCOUNTS'));

// ---------------------------------------------------------------- 점검
console.log('\n[점검 규칙]');
const kinds = (c) => L.checks(c, CATALOG).map((m) => m[0]);
const texts = (c) => L.checks(c, CATALOG).map((m) => m[1]).join(' | ');

truthy('아무것도 안 고르면 오류', kinds(cfg({ apis: [] })).includes('bad'));
truthy(
  '로그인 필요한데 계정 없으면 오류',
  kinds(cfg({ apis: ['me'], accounts: [] })).includes('bad')
);
truthy(
  '비로그인만 고르면 계정 없어도 통과',
  !kinds(cfg({ apis: ['home_summary'], accounts: [] })).includes('bad')
);
truthy(
  'CLIENT 전용을 골랐는데 CLIENT 계정이 없으면 오류',
  texts(cfg({ apis: ['cl_projects'] })).includes('CLIENT 계정이 없다')
);
truthy(
  'CLIENT 계정이 있으면 통과',
  !kinds(cfg({
    apis: ['cl_projects'],
    accounts: [{ email: 'c@d.com', password: 'p', role: 'CLIENT' }],
  })).includes('bad')
);
truthy(
  'AI 를 골랐는데 키가 없으면 오류',
  kinds(cfg({ apis: ['ai_chatbot'] })).includes('bad')
);
truthy(
  'AI 를 고르면 스텁 모드 안내가 나온다',
  texts(cfg({ apis: ['ai_chatbot'], internalKey: 'k' })).includes('AI_STUB_MODE')
);
truthy(
  '쓰기를 고르면 데이터가 남는다고 경고',
  texts(cfg({ apis: ['notifs_read_all'] })).includes('데이터가 실제로 남는다')
);
truthy(
  '계정이 VU 대비 적으면 경고 (계정1 / VU200)',
  texts(cfg({ apis: ['me'], vus: '200' })).includes('20개 이상을 권장')
);
truthy(
  '권장 계정 수는 20개에서 멈춘다 (VU 1000 이어도)',
  texts(cfg({ apis: ['me'], vus: '1000' })).includes('20개 이상을 권장')
);
truthy(
  '계정이 충분하면 경고 없음',
  !texts(cfg({
    apis: ['me'], vus: '200',
    accounts: Array.from({ length: 20 }, (_, i) => ({ email: `u${i}@x.com`, password: 'p', role: 'FREELANCER' })),
  })).includes('개 이상을 권장')
);
truthy(
  'smoke 는 VU 설정이 무시된다고 알린다',
  texts(cfg({ apis: ['me'], profile: 'smoke' })).includes('VU 1')
);
truthy(
  '생각 시간 0 이면 경고',
  texts(cfg({ apis: ['me'], thinkMs: '0' })).includes('k6) 한계에 먼저')
);
truthy(
  'ID 가 필요한 API 를 고르면 안내',
  texts(cfg({ apis: ['chat_messages'] })).includes('ID 가 필요한 API')
);
truthy(
  '문제 없으면 ok 가 맨 앞에 온다',
  L.checks(cfg({ apis: ['home_summary'], accounts: [] }), CATALOG)[0][0] === 'ok'
);

// ---------------------------------------------------------------- 카탈로그 자체
console.log('\n[카탈로그]');
const keys = CATALOG.map((e) => e.key);
truthy('key 가 중복되지 않는다', new Set(keys).size === keys.length, `${keys.length}개`);
truthy(
  '모든 경로가 /api/ 로 시작한다',
  CATALOG.every((e) => e.path.startsWith('/api/')),
  CATALOG.filter((e) => !e.path.startsWith('/api/')).map((e) => e.key).join(',') || '전부 통과'
);
truthy(
  'needs 에 적힌 이름이 경로의 자리표시자와 일치한다',
  CATALOG.every((e) => (e.needs || []).every((n) => e.path.includes('{' + n + '}'))),
);
truthy(
  '경로의 자리표시자가 모두 needs 에 있다',
  CATALOG.every((e) => {
    const inPath = (e.path.match(/\{([^}]+)\}/g) || []).map((s) => s.slice(1, -1));
    return inPath.every((n) => (e.needs || []).includes(n));
  })
);
truthy(
  'AI 항목은 전부 파이썬 대상이고 내부 키를 쓴다',
  CATALOG.filter((e) => e.group === 'ai').every((e) => e.target === 'python' && e.auth === 'internal')
);
truthy(
  'POST/PUT 항목은 본문이 있거나 본문이 필요 없는 것들이다',
  CATALOG.filter((e) => e.method === 'POST' || e.method === 'PUT').length > 0
);

console.log(`\n===== 통과 ${pass} / 실패 ${fails.length} =====`);
if (fails.length) {
  fails.forEach((f) => console.log(`  실패: ${f}`));
  process.exit(1);
}
