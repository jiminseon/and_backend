import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    alert_detect: {
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
    http_req_duration: ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const STOCKS = ['005930', '000660', '035420', '035720', '005380'];

export default function () {
  const stockCode = STOCKS[__ITER % STOCKS.length];
  const res = http.post(`${BASE_URL}/load-test/alerts/detect?stockCode=${stockCode}`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has elapsedMs': (r) => r.json('elapsedMs') !== undefined,
  });
  sleep(1);
}
