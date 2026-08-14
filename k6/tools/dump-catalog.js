/**
 * endpoints.js 를 builder/catalog.js 로 내보낸다.
 *
 *   k6 run k6/tools/dump-catalog.js
 *
 * 왜 이런 도구가 필요한가
 *   빌더 GUI(builder/index.html)는 브라우저에서 file:// 로 그냥 열려야 한다. 그런데 file://
 *   에서는 ES 모듈 import 와 fetch 가 모두 막혀서, HTML 이 endpoints.js 를 직접 읽을 수 없다.
 *
 *   그래서 목록을 전역 변수에 담은 **일반 스크립트**(catalog.js)로 한 번 변환해 둔다.
 *   일반 스크립트는 file:// 에서도 <script src> 로 잘 불린다.
 *
 *   목록을 HTML 에 손으로 옮겨 적는 방법도 있지만, 그러면 endpoints.js 와 두 벌이 되어
 *   조용히 어긋난다. 실제 모듈을 k6 로 읽어서 뽑으면 어긋날 수가 없다.
 *
 * endpoints.js 를 고쳤으면 이 명령을 다시 돌린다. CI 가 재생성 결과와 커밋된 파일을
 * 비교해서, 안 돌렸으면 실패한다.
 */

import { ENDPOINTS, GROUP_LABELS } from '../endpoints.js';

export const options = { vus: 1, iterations: 1 };

export default function () {
  // 아무것도 하지 않는다. 실제 작업은 handleSummary 에서 파일을 쓰는 것이다.
}

export function handleSummary() {
  const plain = ENDPOINTS.map((e) => ({
    key: e.key,
    method: e.method,
    path: e.path,
    target: e.target,
    auth: e.auth,
    group: e.group,
    label: e.label || '',
    warn: e.warn || '',
    needs: e.needs || [],
    // 함수는 JSON 으로 안 나가므로 "본문이 있는지"만 남긴다. GUI 가 표시에 쓴다.
    hasBody: !!e.body,
  }));

  const content =
    '/* 자동 생성 파일 — 직접 고치지 않는다.\n' +
    ' *\n' +
    ' * 원본은 k6/endpoints.js 다. 다시 만들려면:\n' +
    ' *   k6 run k6/tools/dump-catalog.js\n' +
    ' *\n' +
    ' * 빌더 GUI 가 file:// 로 열려야 해서 일반 스크립트(전역 변수)로 내보낸다.\n' +
    ' * ES 모듈은 file:// 에서 import 가 막힌다.\n' +
    ' */\n' +
    'window.K6_CATALOG = ' +
    JSON.stringify(plain, null, 2) +
    ';\n' +
    'window.K6_GROUP_LABELS = ' +
    JSON.stringify(GROUP_LABELS, null, 2) +
    ';\n';

  return { 'k6/builder/catalog.js': content, stdout: `catalog.js 생성 완료 (${plain.length}개)\n` };
}
