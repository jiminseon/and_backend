import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    multicast: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const TOKEN_COUNT = __ENV.TOKEN_COUNT || '500';

export default function () {
  const res = http.post(`${BASE_URL}/load-test/notifications/multicast?tokenCount=${TOKEN_COUNT}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has elapsedMs': (r) => r.json('elapsedMs') !== undefined,
  });
  sleep(1);
}
