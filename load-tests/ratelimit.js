import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const API_KEY = __ENV.API_KEY;
const ALLOWED_ENDPOINT = __ENV.ALLOWED_ENDPOINT || '/health';
const LIMITED_ENDPOINT = __ENV.LIMITED_ENDPOINT || '/api/users';
const HTTP_METHOD = __ENV.HTTP_METHOD || 'GET';

const requestCount = new Counter('requests_total');
const httpErrorRate = new Rate('http_error_rate');
const denialRatio = new Rate('rate_limit_denial_ratio');

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '1m',
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    requests_total: ['rate>10'],
    http_error_rate: ['rate<0.01'],
    rate_limit_denial_ratio: ['rate>0.05', 'rate<0.80'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

function pickTarget() {
  if (Math.random() < 0.8) {
    return { endpoint: ALLOWED_ENDPOINT, bucket: 'under_limit' };
  }
  return { endpoint: LIMITED_ENDPOINT, bucket: 'rate_limited' };
}

function buildPayload(endpoint) {
  return JSON.stringify({
    endpoint,
    method: HTTP_METHOD,
  });
}

export function setup() {
  if (!API_KEY) {
    throw new Error('API_KEY is required. Example: API_KEY=rlim_xxx k6 run load-tests/ratelimit.js');
  }

  return {
    startedAt: new Date().toISOString(),
  };
}

export default function () {
  const target = pickTarget();

  const res = http.post(
    `${BASE_URL}/api/v1/ratelimit/check`,
    buildPayload(target.endpoint),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': API_KEY,
      },
      tags: {
        test_bucket: target.bucket,
        endpoint: target.endpoint,
      },
    }
  );

  requestCount.add(1);

  const transportOrServerError = res.status === 0 || res.status >= 500;
  httpErrorRate.add(transportOrServerError);

  let denied = res.status === 429;
  try {
    const body = res.json();
    if (body && typeof body.allowed === 'boolean') {
      denied = !body.allowed;
    }
  } catch (_) {
    // Keep status-code fallback when response is non-JSON.
  }
  denialRatio.add(denied);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });
}

export function handleSummary(data) {
  const duration = data.state?.testRunDurationMs || 0;
  const seconds = duration > 0 ? duration / 1000 : null;

  const total = data.metrics.http_reqs?.values?.count || 0;
  const throughput =
    data.metrics.requests_total?.values?.rate ||
    data.metrics.http_reqs?.values?.rate ||
    (seconds ? total / seconds : 0);

  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] || 0;
  const p99 = data.metrics.http_req_duration?.values?.['p(99)'] || 0;
  const errors = data.metrics.http_error_rate?.values?.rate || 0;
  const denied = data.metrics.rate_limit_denial_ratio?.values?.rate || 0;

  const lines = [
    '=============================================',
    'Rate Limiter k6 Load Test Summary',
    '=============================================',
    `Base URL                 : ${BASE_URL}`,
    `HTTP method              : ${HTTP_METHOD}`,
    `Under-limit endpoint     : ${ALLOWED_ENDPOINT}`,
    `Rate-limited endpoint    : ${LIMITED_ENDPOINT}`,
    '---------------------------------------------',
    `Total requests           : ${total}`,
    `Throughput (req/s)       : ${throughput.toFixed(2)}`,
    `Latency p95 (ms)         : ${p95.toFixed(2)}`,
    `Latency p99 (ms)         : ${p99.toFixed(2)}`,
    `HTTP error rate          : ${(errors * 100).toFixed(2)}%`,
    `Rate-limit denial ratio  : ${(denied * 100).toFixed(2)}%`,
    '=============================================',
    '',
  ];

  return {
    stdout: lines.join('\n'),
  };
}
