import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 메인 홍보 후기 조회 부하 테스트. (최적화 전/후 비교용)
 *
 * 비로그인 API 라 토큰이 필요 없다. 그래서 스크립트가 짧고, 인증 만료 같은 변수도 없다.
 *
 * 실행:
 *   K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT=report-before.html \
 *     k6 run K6/review/01-home-site-reviews.js
 *
 * BASE_URL 로 대상 서버를 바꾼다. 기본은 로컬이다.
 *   k6 run -e BASE_URL=https://52pairing.kro.kr K6/review/01-home-site-reviews.js
 *
 * 전/후를 같은 조건에서 재려면 아래를 지켜야 한다.
 *   - 같은 장비, 같은 DB, 같은 더미 데이터
 *   - 첫 30초는 예열 구간이다. 그 구간 수치로 비교하지 않는다
 *   - 서버를 재시작한 직후에는 JIT 예열이 안 돼 느리다. 한 번 버리고 두 번째를 쓴다
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 화면이 실제로 쓰는 최대값. 상한이 20 이라 그 이상은 400 이 난다.
const SIZE = __ENV.SIZE || 20;

export const options = {
    stages: [
        { duration: '30s', target: 30 },   // 예열 — 이 구간 수치는 버린다
        { duration: '1m', target: 100 },   // 병목 관찰 구간
        { duration: '30s', target: 0 },    // 감소
    ],
    thresholds: {
        // 최적화 전에는 이걸 넘길 수 있다. 넘겨도 테스트는 끝까지 돈다(리포트가 목적이라).
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/v1/home/site-reviews?size=${SIZE}`, {
        tags: { endpoint: 'home-site-reviews' },
    });

    check(res, {
        '200 응답': (r) => r.status === 200,
        // 후기가 0건이면 양쪽 다 즉시 반환돼서 비교가 무의미해진다. 여기서 걸러진다.
        '후기가 비어 있지 않다': (r) => {
            try {
                return JSON.parse(r.body).data.length > 0;
            } catch (e) {
                return false;
            }
        },
    });

    sleep(1); // 실사용자처럼 think time
}
