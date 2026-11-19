// Usage: docker compose --profile dev --profile load run --rm k6
// Env:   BASE_URL (default http://localhost:8080), PEAK_VUS (default 300), PINNED=false to skip the pinning scenario
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PEAK = parseInt(__ENV.PEAK_VUS || '30000');
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const ramp = (peak) => [
  { duration: '20s', target: peak },
  { duration: '60s', target: peak },
  { duration: '10s', target: 0 },
];

export const options = {
  scenarios: {
    // realistic CRUD traffic: exercises Tomcat + JPA + H2 (and the 10-connection Hikari pool)
    crud: { executor: 'ramping-vus', exec: 'crud', stages: ramp(PEAK) },
    // lots of concurrent requests blocked on "I/O": virtual threads shine here
    slow: { executor: 'ramping-vus', exec: 'slow', stages: ramp(PEAK) },
    // blocking inside synchronized: pins virtual threads on Java 21
    ...(__ENV.PINNED === 'false' ? {} : {
      pinned: { executor: 'constant-vus', exec: 'pinned', vus: 10, duration: '90s', startTime: '10s' },
    }),
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{scenario:crud}': ['p(95)<500'],
  },
};

export function crud() {
  const name = `u${__VU}_${__ITER}`;
  const user = http.post(`${BASE}/api/v1/users`,
    JSON.stringify({ username: name, email: `${name}@example.com` }), JSON_HEADERS);
  if (!check(user, { 'user created': (r) => r.status === 201 })) return;
  const uid = user.json('id');

  const post = http.post(`${BASE}/api/v1/posts`,
    JSON.stringify({ authorId: uid, content: `hello from ${name}` }), JSON_HEADERS);
  check(post, { 'post created': (r) => r.status === 201 });
  const pid = post.json('id');

  http.post(`${BASE}/api/v1/posts/${pid}/comments`,
    JSON.stringify({ authorId: uid, content: 'first!' }), JSON_HEADERS);
  check(http.get(`${BASE}/api/v1/posts?size=20`), { 'list ok': (r) => r.status === 200 });
  check(http.get(`${BASE}/api/v1/posts/${pid}`), { 'get ok': (r) => r.status === 200 });
  http.put(`${BASE}/api/v1/posts/${pid}`, JSON.stringify({ content: 'edited' }), JSON_HEADERS);
  check(http.del(`${BASE}/api/v1/users/${uid}`), { 'user deleted': (r) => r.status === 204 });
  sleep(0.2);
}

export function slow() {
  const r = http.get(`${BASE}/api/v1/system/slow?millis=500`);
  check(r, { 'slow ok': (x) => x.status === 200, 'virtual': (x) => x.json('virtual') === true });
}

export function pinned() {
  check(http.get(`${BASE}/api/v1/system/pinned?millis=100`), { 'pinned ok': (r) => r.status === 200 });
}
