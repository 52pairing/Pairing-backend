/**
 * STOMP over WebSocket 헬퍼.
 *
 * k6 는 STOMP 를 모른다. 프레임을 직접 만들어 보낸다 — 형식이 단순해서 어렵지 않다.
 *
 *     COMMAND\n
 *     header:value\n
 *     \n
 *     body\0            <- 끝은 널 문자다. 이게 없으면 서버가 프레임 경계를 못 잡는다
 *
 * ---------------------------------------------------------------------------
 * 이 서비스에 맞춘 부분
 *
 * 1) 핸드셰이크 인증은 **쿠키**다
 *    JwtHandshakeInterceptor 가 accessToken 쿠키만 읽는다. 쿼리 파라미터로 토큰을 넘기는
 *    흔한 방식은 여기선 거절된다(주석에 이유가 적혀 있다). Bearer 헤더도 안 통한다.
 *
 * 2) SockJS 를 쓰지 않는다
 *    StompWebSocketConfig 에 `.withSockJS()` 가 없다. 그래서 `/info` 협상 없이 ws:// 로
 *    바로 붙는다. SockJS 경로(`/ws/000/xxx/websocket`)로 붙으면 실패한다.
 *
 * 3) 경로는 /api/ws
 *    ALB 가 /api/* 만 백엔드로 보내기 때문에 `/ws` 는 실제 배포에서 백엔드에 닿지 못한다.
 *    StompWebSocketConfig 의 ALWAYS_ON_ENDPOINT 가 그래서 /api/ws 로 고정되어 있다.
 *
 * 4) 하트비트 10초
 *    서버가 heart-beat:10000,10000 으로 협상한다. 우리도 같은 값을 보내고, 10초마다
 *    개행 한 글자를 보내야 서버가 연결을 끊지 않는다. 이걸 빠뜨리면 부하와 무관하게
 *    연결이 죽어서 "부하를 받으면 WebSocket 이 끊긴다"는 잘못된 결론이 나온다.
 */

/**
 * 프레임 종결자.
 *
 * 널 문자를 이스케이프로 적는다. 소스에 실제 널 바이트를 넣으면 git 과 grep 이 파일을
 * 바이너리로 취급해서 diff 도 검색도 안 된다(실제로 그렇게 됐다).
 */
const NULL = '\u0000';

/** 서버가 협상하는 하트비트 주기(ms). StompWebSocketConfig.HEARTBEAT 과 같아야 한다. */
export const HEARTBEAT_MS = 10000;

/** 프레임 한 개를 만든다. */
export function frame(command, headers, body) {
  let text = command + '\n';
  for (const key of Object.keys(headers || {})) {
    text += `${key}:${headers[key]}\n`;
  }
  return text + '\n' + (body || '') + NULL;
}

/** CONNECT. accept-version 을 안 보내면 서버가 버전을 못 정해 거절한다. */
export function connectFrame(host) {
  return frame('CONNECT', {
    'accept-version': '1.2',
    host: host,
    'heart-beat': `${HEARTBEAT_MS},${HEARTBEAT_MS}`,
  });
}

/** SUBSCRIBE. id 는 연결 안에서 유일해야 한다. */
export function subscribeFrame(id, destination) {
  return frame('SUBSCRIBE', { id: id, destination: destination });
}

export function disconnectFrame() {
  return frame('DISCONNECT', { receipt: 'bye' });
}

/** 서버가 보낸 프레임의 명령어를 읽는다. (CONNECTED / MESSAGE / ERROR / RECEIPT) */
export function commandOf(raw) {
  const text = String(raw || '');
  const end = text.indexOf('\n');
  return (end === -1 ? text : text.slice(0, end)).trim();
}

/**
 * 이 계정이 구독할 경로들.
 *
 * 실제 화면이 구독하는 것과 같은 경로를 쓴다. 알림은 계정별이고, 채팅·협상은 방/건별이다.
 * (NotificationStompAdapter / ChatEventStompAdapter / NegotiationEventStompAdapter 참고)
 */
export function destinationsFor(account, ids) {
  const list = [];
  if (account && account.accountId) {
    list.push(`/topic/users/${account.accountId}/notifications`);
  }
  if (ids && ids.chatRoomId) list.push(`/topic/chat-rooms/${ids.chatRoomId}`);
  if (ids && ids.negotiationId) list.push(`/topic/negotiations/${ids.negotiationId}`);
  return list;
}

/** ws:// 또는 wss:// 핸드셰이크 URL. BASE_URL 의 스킴을 따라간다. */
export function websocketUrl(baseUrl) {
  return baseUrl.replace(/^http:/, 'ws:').replace(/^https:/, 'wss:') + '/api/ws';
}
