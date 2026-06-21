import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    notification_send_mode: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 20 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<10000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const TOKEN_COUNT = __ENV.TOKEN_COUNT || '500';
const SEND_MODE = __ENV.SEND_MODE || 'multicast';

const endpoint = SEND_MODE === 'single-loop' ? 'single-loop' : 'multicast';

export default function () {
  const res = http.post(`${BASE_URL}/load-test/notifications/${endpoint}?tokenCount=${TOKEN_COUNT}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'mode matches': (r) => r.json('mode') === endpoint,
    'has elapsedMs': (r) => r.json('elapsedMs') !== undefined,
  });
  sleep(1);
}
