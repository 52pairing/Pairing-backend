/**
 * 실행 결과를 HTML 리포트와 JSON 으로 남긴다.
 *
 * 원격 라이브러리(jslib.k6.io)를 가져오지 않고 직접 만든다. 원격 import 는 k6 가 실행할 때
 * 네트워크로 받아오는데, 부하 테스트를 돌리려는 순간에 그게 막히면 실행 자체가 안 된다.
 * 리포트 하나 때문에 테스트를 못 도는 상황을 만들지 않는다.
 *
 * 남기는 것
 *   reports/<testid>.html  사람이 보는 리포트. 파일 하나로 완결되어 그대로 공유할 수 있다
 *   reports/<testid>.json  기계가 읽는 원본. compare.html 에 두 개를 넣으면 개선 전/후를 비교한다
 */

/** 밀리초를 읽기 좋게. */
function ms(value) {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  if (value >= 1000) return `${(value / 1000).toFixed(2)}s`;
  return `${value.toFixed(1)}ms`;
}

function pct(value) {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return `${(value * 100).toFixed(2)}%`;
}

function num(value, digits) {
  if (value === undefined || value === null || Number.isNaN(value)) return '-';
  return value.toFixed(digits === undefined ? 2 : digits);
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * 엔드포인트별 통계를 뽑는다.
 *
 * k6 는 태그별 하위 메트릭을 요약에 담지 않는다. 그래서 main.js 가 엔드포인트마다 별도
 * Trend 를 만들어 두고(`ep_<key>`), 여기서 그 이름으로 찾아 표를 만든다.
 *
 * 호출 건수(count)는 options.summaryTrendStats 에 'count' 를 넣어야 나온다. 기본값에는
 * 없어서, 빼먹으면 이 표의 건수가 전부 0 이 된다.
 */
function endpointRows(metrics) {
  const rows = [];
  for (const name of Object.keys(metrics)) {
    if (name.indexOf('ep_') !== 0) continue;
    const key = name.slice(3);
    const metric = metrics[name];
    const values = metric.values || {};
    rows.push({
      key,
      count: values.count || 0,
      avg: values.avg,
      p95: values['p(95)'],
      p99: values['p(99)'],
      max: values.max,
    });
  }
  // 느린 순. 개선 대상을 위에서부터 읽을 수 있게 한다.
  rows.sort((a, b) => (b.p95 || 0) - (a.p95 || 0));
  return rows;
}

/**
 * VU 구간별 통계. stress 프로파일에서만 값이 있다.
 *
 * 이 표가 한계점 테스트의 핵심이다. 전체 p95 하나로는 "몇 명에서 무너졌는가"를 알 수 없고,
 * 여기서 지연이 갑자기 꺾이는 줄이 곧 그 답이다.
 */
function vuRows(metrics) {
  const rows = [];
  for (const name of Object.keys(metrics)) {
    const m = name.match(/^vu_(\d+)_ms$/);
    if (!m) continue;
    const size = Number(m[1]);
    const values = (metrics[name] || {}).values || {};
    const reqs = ((metrics[`vu_${size}_reqs`] || {}).values || {}).count || 0;
    const fails = ((metrics[`vu_${size}_fail`] || {}).values || {}).count || 0;
    rows.push({
      size,
      reqs,
      fails,
      failRate: reqs ? fails / reqs : 0,
      avg: values.avg,
      p95: values['p(95)'],
      p99: values['p(99)'],
      max: values.max,
    });
  }
  rows.sort((a, b) => a.size - b.size);

  // 앞 구간 대비 p95 가 얼마나 나빠졌는지. 급증하는 줄이 무릎이다.
  for (let i = 0; i < rows.length; i++) {
    const prev = i > 0 ? rows[i - 1].p95 : null;
    rows[i].growth = prev && prev > 0 && rows[i].p95 ? rows[i].p95 / prev : null;
  }
  return rows;
}

function failureRows(metrics) {
  const rows = [];
  for (const name of Object.keys(metrics)) {
    if (name.indexOf('fail_') !== 0) continue;
    const values = (metrics[name] || {}).values || {};
    if (!values.count) continue;
    rows.push({ key: name.slice(5), count: values.count });
  }
  rows.sort((a, b) => b.count - a.count);
  return rows;
}

function thresholdRows(metrics) {
  const rows = [];
  for (const name of Object.keys(metrics)) {
    const thresholds = (metrics[name] || {}).thresholds;
    if (!thresholds) continue;
    for (const expr of Object.keys(thresholds)) {
      rows.push({ metric: name, expr, ok: thresholds[expr].ok === true });
    }
  }
  return rows;
}

/** 텍스트 요약. 터미널에 그대로 찍는다. */
export function textSummary(data, meta) {
  const m = data.metrics || {};
  const dur = (m.http_req_duration || {}).values || {};
  const failed = (m.http_req_failed || {}).values || {};
  const reqs = (m.http_reqs || {}).values || {};
  const checks = (m.checks || {}).values || {};

  const lines = [];
  lines.push('');
  lines.push('='.repeat(64));
  lines.push(`  실행 ID : ${meta.testid}`);
  lines.push(`  프로파일: ${meta.profile}   대상: ${meta.baseUrl}`);
  lines.push('='.repeat(64));
  lines.push(`  총 요청      ${num(reqs.count, 0)} 건   (${num(reqs.rate, 2)} req/s)`);
  lines.push(`  실패율       ${pct(failed.rate)}`);
  lines.push(`  체크 성공률  ${pct(checks.rate)}`);
  lines.push(`  지연 avg     ${ms(dur.avg)}`);
  lines.push(`  지연 p95     ${ms(dur['p(95)'])}`);
  lines.push(`  지연 p99     ${ms(dur['p(99)'])}`);
  lines.push(`  지연 max     ${ms(dur.max)}`);
  lines.push('');

  const vus = vuRows(m);
  if (vus.length) {
    lines.push('  동시접속 구간별 (한계점)');
    lines.push('    동시접속   요청     p95        앞구간대비   실패율');
    // 한계는 **처음 무너진 구간 하나만** 표시한다. 조건에 맞는 줄을 전부 표시하면
    // 그 뒤 구간이 다 걸려서(이미 무너진 상태이므로) 어디가 시작인지 안 보인다.
    let kneeMarked = false;
    for (const row of vus) {
      const growth = row.growth === null ? '-' : row.growth.toFixed(1) + '배';
      const broke = (row.growth !== null && row.growth >= 2) || row.failRate >= 0.01;
      const knee = broke && !kneeMarked ? '  ← 한계' : '';
      if (broke) kneeMarked = true;
      lines.push(
        `    ${String(row.size).padStart(6)}명  ${String(row.reqs).padStart(6)}  ` +
          `${ms(row.p95).padStart(9)}  ${growth.padStart(9)}  ${pct(row.failRate).padStart(7)}${knee}`
      );
    }
    lines.push('');
  }

  const slow = endpointRows(m).slice(0, 8);
  if (slow.length) {
    lines.push('  느린 엔드포인트 (p95 기준)');
    for (const row of slow) {
      lines.push(`    ${row.key.padEnd(20)} p95 ${ms(row.p95).padStart(9)}   ${row.count} 건`);
    }
    lines.push('');
  }

  const failures = failureRows(m);
  if (failures.length) {
    lines.push('  실패한 엔드포인트');
    for (const row of failures) lines.push(`    ${row.key.padEnd(20)} ${row.count} 건`);
    lines.push('');
  }

  lines.push(`  리포트: k6/reports/${meta.testid}.html`);
  lines.push('');
  return lines.join('\n');
}

/**
 * 동시접속 구간별 표. 한계점(무릎)을 읽는 자리다.
 *
 * 판정 기준
 *   지연 배수 2배 이상  또는  실패율 1% 이상  이면 그 구간을 한계로 본다.
 *   둘 중 먼저 오는 쪽이 실제 한계다 — 보통 지연이 먼저 무너지고 실패가 뒤따른다.
 */
function vuSection(metrics) {
  const rows = vuRows(metrics);
  if (!rows.length) return '';

  // 처음으로 무너진 구간을 찾는다.
  let kneeIndex = -1;
  for (let i = 0; i < rows.length; i++) {
    const brokeLatency = rows[i].growth !== null && rows[i].growth >= 2;
    const brokeErrors = rows[i].failRate >= 0.01;
    if (brokeLatency || brokeErrors) {
      kneeIndex = i;
      break;
    }
  }

  const verdict =
    kneeIndex === -1
      ? `<div class="note ok">끝까지 무너지지 않았다. 마지막 구간(동시 ${rows[rows.length - 1].size}명)까지 ` +
        `지연이 2배 미만이고 실패율도 1% 미만이다. <b>한계를 더 보려면 VU 를 올려서 다시 돌린다.</b></div>`
      : `<div class="note bad">동시 <b>${rows[kneeIndex].size}명</b> 구간에서 무너진다. ` +
        `직전 구간(${kneeIndex > 0 ? rows[kneeIndex - 1].size + '명' : '시작'}) 대비 ` +
        `지연 ${rows[kneeIndex].growth ? rows[kneeIndex].growth.toFixed(1) + '배' : '-'}, ` +
        `실패율 ${pct(rows[kneeIndex].failRate)}. <b>이 서비스의 현재 한계는 그 앞 구간까지다.</b></div>`;

  const body = rows
    .map((r, i) => {
      const isKnee = i === kneeIndex;
      const growth = r.growth === null ? '-' : r.growth.toFixed(1) + '배';
      const growthClass = r.growth === null ? '' : r.growth >= 2 ? 'bad' : r.growth >= 1.5 ? 'warn' : 'ok';
      const failClass = r.failRate >= 0.01 ? 'bad' : r.failRate > 0 ? 'warn' : 'ok';
      return (
        `    <tr${isKnee ? ' style="background:rgba(248,113,113,.12)"' : ''}>` +
        `<td>${r.size}명${isKnee ? ' ← 한계' : ''}</td>` +
        `<td>${num(r.reqs, 0)}</td>` +
        `<td>${ms(r.avg)}</td>` +
        `<td>${ms(r.p95)}</td>` +
        `<td>${ms(r.p99)}</td>` +
        `<td class="${growthClass}">${growth}</td>` +
        `<td class="${failClass}">${pct(r.failRate)}</td></tr>`
      );
    })
    .join('\n');

  return `
<h2>동시접속 구간별 — 한계점</h2>
${verdict}
<div class="scroll"><table>
  <thead><tr>
    <th>동시접속</th><th>요청</th><th>평균</th><th>p95</th><th>p99</th>
    <th>앞 구간 대비</th><th>실패율</th>
  </tr></thead>
  <tbody>
${body}
  </tbody>
</table></div>
<div class="note">
  같은 표를 개선 후에 다시 뽑아 나란히 놓으면 "한계가 몇 명에서 몇 명으로 늘었는지"가 바로 보인다.
  이 그래프가 개선을 가장 설득력 있게 보여준다.
</div>`;
}

/** 단일 파일 HTML 리포트. */
export function htmlReport(data, meta) {
  const m = data.metrics || {};
  const dur = (m.http_req_duration || {}).values || {};
  const failed = (m.http_req_failed || {}).values || {};
  const reqs = (m.http_reqs || {}).values || {};
  const checks = (m.checks || {}).values || {};
  const vus = (m.vus_max || {}).values || {};

  const rows = endpointRows(m);
  const failures = failureRows(m);
  const thresholds = thresholdRows(m);
  const allPassed = thresholds.every((t) => t.ok);

  const card = (label, value, hint) => `
      <div class="card">
        <div class="label">${escapeHtml(label)}</div>
        <div class="value">${escapeHtml(value)}</div>
        ${hint ? `<div class="hint">${escapeHtml(hint)}</div>` : ''}
      </div>`;

  return `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>k6 리포트 · ${escapeHtml(meta.testid)}</title>
<style>
  :root { --fg:#e8ecf4; --dim:#98a2b8; --line:rgba(255,255,255,.10); --card:rgba(17,21,33,.6);
          --ok:#4ade80; --bad:#f87171; --warn:#fbbf24; --accent:#7aa2ff; }
  * { box-sizing:border-box; }
  body { margin:0; background:#05070f; color:var(--fg); padding:40px 20px 80px;
         font:15px/1.6 -apple-system,"Segoe UI","Malgun Gothic",sans-serif; }
  .wrap { max-width:1000px; margin:0 auto; }
  h1 { font-size:22px; margin:0 0 4px; }
  h2 { font-size:16px; margin:36px 0 12px; color:var(--accent); }
  .sub { color:var(--dim); font-size:13.5px; margin-bottom:28px; }
  .verdict { display:inline-block; padding:4px 12px; border-radius:999px; font-size:13px; font-weight:600; }
  .pass { background:rgba(74,222,128,.15); color:var(--ok); border:1px solid rgba(74,222,128,.4); }
  .fail { background:rgba(248,113,113,.15); color:var(--bad); border:1px solid rgba(248,113,113,.4); }
  .cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(150px,1fr)); gap:12px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:14px 16px; }
  .card .label { color:var(--dim); font-size:12.5px; }
  .card .value { font-size:22px; font-weight:600; margin-top:2px; }
  .card .hint { color:var(--dim); font-size:11.5px; margin-top:2px; }
  .scroll { overflow-x:auto; }
  table { border-collapse:collapse; width:100%; font-size:13.5px; min-width:560px; }
  th,td { text-align:right; padding:8px 10px; border-bottom:1px solid var(--line); white-space:nowrap; }
  th:first-child, td:first-child { text-align:left; }
  th { color:var(--dim); font-weight:500; font-size:12.5px; }
  tbody tr:hover { background:rgba(255,255,255,.03); }
  .bad { color:var(--bad); } .ok { color:var(--ok); } .warn { color:var(--warn); }
  .note { color:var(--dim); font-size:12.5px; border-left:2px solid var(--line);
          padding:2px 0 2px 12px; margin:10px 0; }
  code { background:rgba(0,0,0,.45); border:1px solid var(--line); border-radius:4px;
         padding:1px 5px; font-size:12px; }
</style></head><body><div class="wrap">

<h1>k6 부하 테스트 리포트</h1>
<div class="sub">
  실행 ID <code>${escapeHtml(meta.testid)}</code> ·
  프로파일 <b>${escapeHtml(meta.profile)}</b> ·
  대상 <code>${escapeHtml(meta.baseUrl)}</code><br>
  ${escapeHtml(meta.startedAt)} · 계정 ${escapeHtml(String(meta.accounts))}개 ·
  API ${escapeHtml(String(meta.apiCount))}개
  &nbsp; <span class="verdict ${allPassed ? 'pass' : 'fail'}">${allPassed ? '기준 통과' : '기준 미달'}</span>
</div>

<div class="cards">
  ${card('총 요청', num(reqs.count, 0) + ' 건', num(reqs.rate, 2) + ' req/s')}
  ${card('실패율', pct(failed.rate), '4xx·5xx·연결 실패')}
  ${card('최대 VU', num(vus.max, 0), '동시 가상 사용자')}
  ${card('지연 p95', ms(dur['p(95)']), '100건 중 95건이 이 안')}
  ${card('지연 p99', ms(dur['p(99)']), '꼬리 지연')}
  ${card('지연 최대', ms(dur.max), '')}
</div>

<div class="note">
  여기 숫자는 <b>클라이언트가 실제로 기다린 시간</b>이다. 네트워크 왕복과 대기열이 모두 들어 있다.
  서버가 처리한 시간만 보려면 Grafana 의 스프링부트 성능 대시보드를 같은 시간대로 맞춰 본다.
  두 값의 차이가 곧 네트워크·큐 대기 시간이다.
</div>

${vuSection(m)}

<h2>엔드포인트별 지연 (p95 느린 순)</h2>
<div class="scroll"><table>
  <thead><tr><th>API</th><th>호출</th><th>평균</th><th>p95</th><th>p99</th><th>최대</th></tr></thead>
  <tbody>
${
  rows.length
    ? rows
        .map(
          (r) => `    <tr><td>${escapeHtml(r.key)}</td><td>${num(r.count, 0)}</td>` +
            `<td>${ms(r.avg)}</td><td>${ms(r.p95)}</td><td>${ms(r.p99)}</td><td>${ms(r.max)}</td></tr>`
        )
        .join('\n')
    : '    <tr><td colspan="6">기록된 엔드포인트가 없다</td></tr>'
}
  </tbody>
</table></div>

<h2>실패</h2>
${
  failures.length
    ? `<div class="scroll"><table>
  <thead><tr><th>API</th><th>실패 건수</th></tr></thead>
  <tbody>
${failures.map((f) => `    <tr><td>${escapeHtml(f.key)}</td><td class="bad">${num(f.count, 0)}</td></tr>`).join('\n')}
  </tbody>
</table></div>`
    : '<div class="note ok">실패한 요청이 없다.</div>'
}

<h2>기준(threshold)</h2>
<div class="scroll"><table>
  <thead><tr><th>지표</th><th>조건</th><th>결과</th></tr></thead>
  <tbody>
${
  thresholds.length
    ? thresholds
        .map(
          (t) =>
            `    <tr><td>${escapeHtml(t.metric)}</td><td>${escapeHtml(t.expr)}</td>` +
            `<td class="${t.ok ? 'ok' : 'bad'}">${t.ok ? '통과' : '미달'}</td></tr>`
        )
        .join('\n')
    : '    <tr><td colspan="3">설정된 기준이 없다</td></tr>'
}
  </tbody>
</table></div>

<h2>제외된 항목</h2>
${
  (meta.skipped || []).length
    ? `<ul class="note">${meta.skipped.map((s) => `<li>${escapeHtml(s)}</li>`).join('')}</ul>`
    : '<div class="note">없다. 고른 API 가 전부 실행됐다.</div>'
}

<div class="note" style="margin-top:36px">
  개선 전/후를 나란히 보려면 <code>k6/compare.html</code> 을 열고 이 실행의
  <code>${escapeHtml(meta.testid)}.json</code> 과 다른 실행의 json 을 함께 넣는다.
</div>

</div></body></html>`;
}
