/**
 * 카탈로그 정합성 검사.
 *
 *   node k6/tools/check-catalog.mjs
 *
 * 두 가지를 본다.
 *
 * 1) builder/catalog.js 가 endpoints.js 와 맞는지
 *    catalog.js 는 자동 생성 파일이다(k6 run k6/tools/dump-catalog.js). endpoints.js 를
 *    고치고 재생성을 잊으면 빌더 GUI 가 옛 목록을 보여준다. 그러면 새로 추가한 API 가
 *    화면에 안 나오거나, 지운 API 가 남아서 실행이 실패한다.
 *
 * 2) 카탈로그의 경로가 실제 컨트롤러에 존재하는지
 *    이게 더 중요하다. 경로에 오타가 있으면 그 API 는 404 를 받는데, **404 는 DB 를 타지
 *    않아서 아주 빠르다.** 그래서 "p95 3ms" 같은 좋아 보이는 숫자가 나온다. 실패율로도
 *    안 잡힌다 — main.js 의 체크가 2xx 를 보므로 잡히긴 하지만, 컨트롤러가 나중에 경로를
 *    바꾼 경우를 미리 알려면 이 검사가 필요하다.
 *
 * k6 도 파이썬도 필요 없다. Node 만으로 돈다(CI 에서 가볍게 돌리려고).
 */

import { readFileSync, existsSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const k6Dir = join(here, '..');
const backendRoot = join(k6Dir, '..');
// 파이썬 레포는 형제 폴더로 가정한다. 없으면 파이썬 경로 검사만 건너뛴다.
const pythonRoot = join(backendRoot, '..', 'Pairing-python');

const problems = [];
const notes = [];

/** 폴더를 재귀로 훑어 조건에 맞는 파일 경로를 모은다. */
function walk(dir, match, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    let info;
    try {
      info = statSync(full);
    } catch {
      continue;
    }
    if (info.isDirectory()) walk(full, match, out);
    else if (match(name)) out.push(full);
  }
  return out;
}

/** 경로 변수 이름을 지운다. {id} 와 {chatRoomId} 를 같은 것으로 본다. */
function shape(path) {
  return path.replace(/\{[^}]*\}/g, '{}').replace(/\/+$/, '') || '/';
}

// ---------------------------------------------------------------------------
// 1) endpoints.js 파싱
// ---------------------------------------------------------------------------
const source = readFileSync(join(k6Dir, 'endpoints.js'), 'utf8');

// ENDPOINTS 배열 부분만 본다. 아래쪽 헬퍼 함수의 문자열을 항목으로 오인하지 않도록.
const arrayStart = source.indexOf('export const ENDPOINTS = [');
const arrayEnd = source.indexOf('\n];', arrayStart);
if (arrayStart === -1 || arrayEnd === -1) {
  console.error('endpoints.js 에서 ENDPOINTS 배열을 찾지 못했다. 이 검사기를 고쳐야 한다.');
  process.exit(2);
}
const arraySrc = source.slice(arrayStart, arrayEnd);

function field(text, name) {
  const m = text.match(new RegExp(name + ":\\s*'([^']*)'"));
  return m ? m[1] : null;
}

// 항목 하나는 key 로 시작해 다음 key 직전까지다.
const chunks = [];
const keyRe = /key:\s*'([^']+)'/g;
const positions = [];
let m;
while ((m = keyRe.exec(arraySrc)) !== null) positions.push({ key: m[1], at: m.index });
positions.forEach((p, i) => {
  const end = i + 1 < positions.length ? positions[i + 1].at : arraySrc.length;
  chunks.push({ key: p.key, text: arraySrc.slice(p.at, end) });
});

const sourceEntries = chunks.map((c) => ({
  key: c.key,
  method: field(c.text, 'method'),
  path: field(c.text, 'path'),
  target: field(c.text, 'target'),
  auth: field(c.text, 'auth'),
  group: field(c.text, 'group'),
}));

const incomplete = sourceEntries.filter((e) => !e.method || !e.path || !e.target || !e.auth || !e.group);
if (incomplete.length) {
  problems.push(
    'endpoints.js 항목에 필수 필드가 빠졌다: ' + incomplete.map((e) => e.key).join(', ')
  );
}

// ---------------------------------------------------------------------------
// 2) catalog.js 와 대조
// ---------------------------------------------------------------------------
const catalogPath = join(k6Dir, 'builder', 'catalog.js');
if (!existsSync(catalogPath)) {
  problems.push(
    'builder/catalog.js 가 없다. 만들려면: k6 run k6/tools/dump-catalog.js'
  );
} else {
  globalThis.window = globalThis;
  new Function(readFileSync(catalogPath, 'utf8'))();
  const catalog = globalThis.K6_CATALOG || [];

  const srcKeys = new Set(sourceEntries.map((e) => e.key));
  const catKeys = new Set(catalog.map((e) => e.key));

  const missing = [...srcKeys].filter((k) => !catKeys.has(k));
  const extra = [...catKeys].filter((k) => !srcKeys.has(k));
  if (missing.length || extra.length) {
    problems.push(
      'builder/catalog.js 가 endpoints.js 와 다르다.\n' +
        (missing.length ? '    catalog.js 에 없는 항목: ' + missing.join(', ') + '\n' : '') +
        (extra.length ? '    catalog.js 에만 있는 항목: ' + extra.join(', ') + '\n' : '') +
        '    재생성: k6 run k6/tools/dump-catalog.js'
    );
  } else {
    // 키가 같아도 메서드·경로가 어긋날 수 있다.
    const byKey = {};
    catalog.forEach((e) => (byKey[e.key] = e));
    const drift = sourceEntries.filter((e) => {
      const c = byKey[e.key];
      return c && (c.method !== e.method || c.path !== e.path || c.auth !== e.auth || c.group !== e.group);
    });
    if (drift.length) {
      problems.push(
        'catalog.js 의 내용이 endpoints.js 와 어긋났다: ' +
          drift.map((e) => e.key).join(', ') +
          '\n    재생성: k6 run k6/tools/dump-catalog.js'
      );
    } else {
      notes.push(`catalog.js 가 endpoints.js 와 일치한다 (${catalog.length}개)`);
    }
  }
}

// ---------------------------------------------------------------------------
// 3) 스프링 컨트롤러 경로 수집
// ---------------------------------------------------------------------------
const springRoutes = new Set();
for (const file of walk(join(backendRoot, 'src', 'main', 'java'), (n) => n.endsWith('Controller.java'))) {
  const text = readFileSync(file, 'utf8');
  const base = text.match(/@RequestMapping\("([^"]+)"\)/);
  const prefix = base ? base[1] : '';
  const re = /@(Get|Post|Put|Delete|Patch)Mapping(\(([^)]*)\))?/g;
  let mm;
  while ((mm = re.exec(text)) !== null) {
    const verb = mm[1].toUpperCase();
    const args = mm[3] || '';
    const sub = args.match(/"([^"]*)"/);
    springRoutes.add(verb + ' ' + shape(prefix + (sub ? sub[1] : '')));
  }
}

// ---------------------------------------------------------------------------
// 4) 파이썬 라우터 경로 수집
// ---------------------------------------------------------------------------
const pythonRoutes = new Set();
let pythonChecked = false;
if (existsSync(join(pythonRoot, 'app'))) {
  pythonChecked = true;
  for (const file of walk(join(pythonRoot, 'app', 'domains'), (n) => n === 'router.py')) {
    const text = readFileSync(file, 'utf8');
    const pre = text.match(/APIRouter\(prefix="([^"]+)"/);
    const prefix = pre ? pre[1] : '';
    const re = /@router\.(get|post|put|delete|patch)\("([^"]*)"/g;
    let mm;
    while ((mm = re.exec(text)) !== null) {
      pythonRoutes.add(mm[1].toUpperCase() + ' ' + shape('/api/v1' + prefix + mm[2]));
    }
  }
} else {
  notes.push(`Pairing-python 을 찾지 못해 AI 경로 검사는 건너뛴다 (${pythonRoot})`);
}

// ---------------------------------------------------------------------------
// 5) 대조
// ---------------------------------------------------------------------------
const unmatched = [];
for (const e of sourceEntries) {
  if (!e.path || !e.method) continue;
  const wanted = e.method + ' ' + shape(e.path);
  if (e.target === 'spring') {
    if (!springRoutes.has(wanted)) unmatched.push(`${e.key}: ${e.method} ${e.path} (스프링 컨트롤러에 없다)`);
  } else if (e.target === 'python' && pythonChecked) {
    if (!pythonRoutes.has(wanted)) unmatched.push(`${e.key}: ${e.method} ${e.path} (파이썬 라우터에 없다)`);
  }
}
if (unmatched.length) {
  problems.push(
    '카탈로그 경로가 실제 컨트롤러에 없다. 컨트롤러가 바뀌었거나 오타다:\n    ' +
      unmatched.join('\n    ') +
      '\n    (없는 경로는 404 를 받는다. 404 는 DB 를 안 타서 빠르므로, 성능이 좋아진 것처럼 보인다)'
  );
} else {
  notes.push(
    `카탈로그 경로 ${sourceEntries.length}개가 모두 실제 컨트롤러에 존재한다 ` +
      `(스프링 ${springRoutes.size} / 파이썬 ${pythonRoutes.size})`
  );
}

// ---------------------------------------------------------------------------
// 결과
// ---------------------------------------------------------------------------
notes.forEach((n) => console.log('  OK   ' + n));
if (problems.length) {
  console.log('');
  problems.forEach((p) => console.log('  실패  ' + p));
  process.exit(1);
}
console.log('\n카탈로그 검사 통과');
