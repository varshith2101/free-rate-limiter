# k6 Rate Limit Load Test

This folder contains a single k6 script that stress-tests:

- `POST /api/v1/ratelimit/check`
- Header: `X-API-Key: <API_KEY>`
- Body shape: `{ "endpoint": "/path", "method": "GET" }`

The script sends an `80/20` split:

- 80% to an endpoint intended to stay under limit
- 20% to an endpoint intended to trigger rate limiting

## Run

Install k6 if needed: https://grafana.com/docs/k6/latest/set-up/install-k6/

```bash
BASE_URL=http://localhost \
API_KEY=rlim_your_api_key_here \
ALLOWED_ENDPOINT=/health \
LIMITED_ENDPOINT=/api/users \
HTTP_METHOD=GET \
VUS=20 \
DURATION=1m \
k6 run load-tests/ratelimit.js
```

## Environment Variables

- `BASE_URL` (required in practice): e.g. `http://localhost`
- `API_KEY` (required): API key used in `X-API-Key`
- `ALLOWED_ENDPOINT` (optional, default: `/health`)
- `LIMITED_ENDPOINT` (optional, default: `/api/users`)
- `HTTP_METHOD` (optional, default: `GET`)
- `VUS` (optional, default: `20`)
- `DURATION` (optional, default: `1m`)

## Metrics and Thresholds

- `p95 latency` (`http_req_duration p(95) < 800ms`): 95% of checks should complete under 800ms.
- `p99 latency` (`http_req_duration p(99) < 1500ms`): 99% of checks should complete under 1500ms.
- `throughput req/s` (`requests_total rate > 10`): minimum sustained request throughput.
- `http error rate` (`http_error_rate < 1%`): only transport errors and 5xx responses count as errors.
- `rate-limit denial ratio` (`rate_limit_denial_ratio between 5% and 80%`): derived from response body `allowed` (with 429 fallback).

## Example Summary Output

```text
=============================================
Rate Limiter k6 Load Test Summary
=============================================
Base URL                 : http://localhost
HTTP method              : GET
Under-limit endpoint     : /health
Rate-limited endpoint    : /api/users
---------------------------------------------
Total requests           : 12345
Throughput (req/s)       : 205.75
Latency p95 (ms)         : 41.27
Latency p99 (ms)         : 89.12
HTTP error rate          : 0.00%
Rate-limit denial ratio  : 18.64%
=============================================
```
