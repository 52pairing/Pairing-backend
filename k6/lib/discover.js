/**
 * 실행 시점에 실제 ID 를 찾아온다.
 *
 * `/api/v1/chat-rooms/{chatRoomId}` 같은 API 는 존재하는 ID 가 있어야 의미 있는 부하가 된다.
 * 없는 ID 로 부르면 전부 404 가 되고, 그건 DB 조회 한 번으로 끝나서 **실제보다 훨씬 빠른
 * 숫자가 나온다.** 그 상태로 "p95 30ms" 라고 말하면 측정이 아니라 착각이다.
 *
 * 그래서 setup() 에서 목록 API 를 한 번 불러 첫 ID 를 꺼내고, 못 찾으면 그 엔드포인트를
 * 조용히 빼는 대신 **경고를 찍고 뺀다**. 무엇이 측정되지 않았는지 리포트에 남아야 한다.
 */

import http from 'k6/http';
import { bearerHeaders } from './auth.js';

/** 어떤 목록 API 에서 어떤 ID 를 꺼낼지. */
const SOURCES = [
  { id: 'chatRoomId', path: '/api/v1/chat-rooms', keys: ['chatRoomId', 'roomId', 'id'] },
  { id: 'negotiationId', path: '/api/v1/negotiations/mine', keys: ['negotiationId', 'id'] },
  { id: 'contractId', path: '/api/v1/contracts', keys: ['contractId', 'id'] },
];

/**
 * 목록 API 를 훑어 ID 를 모은다.
 *
 * @param overrides 사용자가 직접 지정한 값. 있으면 조회하지 않고 그대로 쓴다.
 */
export function discoverIds(baseUrl, account, overrides) {
  const ids = {};

  for (const source of SOURCES) {
    if (overrides && overrides[source.id] !== undefined && overrides[source.id] !== null) {
      ids[source.id] = overrides[source.id];
      console.log(`[ID] ${source.id}=${ids[source.id]} (직접 지정)`);
      continue;
    }

    const res = http.get(`${baseUrl}${source.path}`, {
      headers: bearerHeaders(account),
      tags: { name: 'setup:discover' },
    });

    if (res.status !== 200) {
      console.warn(`[ID] ${source.id} 조회 실패: ${source.path} -> HTTP ${res.status}`);
      continue;
    }

    let body;
    try {
      body = res.json();
    } catch (e) {
      console.warn(`[ID] ${source.id}: 응답이 JSON 이 아니다`);
      continue;
    }

    const found = firstNumber(body, source.keys, 0);
    if (found === null) {
      console.warn(
        `[ID] ${source.id} 를 못 찾았다 (${source.path} 의 응답이 비어 있다). ` +
          `이 ID 가 필요한 API 는 이번 실행에서 제외된다.`
      );
      continue;
    }

    ids[source.id] = found;
    console.log(`[ID] ${source.id}=${found}`);
  }

  return ids;
}

/**
 * 응답 어딘가에서 주어진 이름의 숫자 필드를 처음 하나 찾는다.
 *
 * 목록 응답의 형태(ApiResponse.data 가 배열인지, 페이지 객체인지, 그 안에 content 가 있는지)가
 * 도메인마다 다를 수 있어서 형태를 가정하지 않고 훑는다. 도메인이 늘어도 이 파일은 그대로다.
 */
function firstNumber(node, keys, depth) {
  if (depth > 6 || node === null || node === undefined) return null;

  if (Array.isArray(node)) {
    for (const item of node) {
      const found = firstNumber(item, keys, depth + 1);
      if (found !== null) return found;
    }
    return null;
  }

  if (typeof node !== 'object') return null;

  // 이 객체가 직접 들고 있는지 먼저 본다. 얕은 쪽이 정답일 가능성이 높다.
  for (const key of keys) {
    const value = node[key];
    if (typeof value === 'number' && Number.isFinite(value)) return value;
  }

  for (const key of Object.keys(node)) {
    const found = firstNumber(node[key], keys, depth + 1);
    if (found !== null) return found;
  }
  return null;
}
