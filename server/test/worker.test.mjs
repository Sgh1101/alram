// Worker 를 실제 HTTP 요청 단위로 시험한다. D1 은 node:sqlite 위에 얇은 흉내(shim)로 대신한다.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import worker, { parseTime } from '../src/index.js';

const INGEST = 'ingest-token-0123456789';
const READ = 'read-token-abcdefghijklmn';

function d1() {
  const db = new DatabaseSync(':memory:');
  db.exec(readFileSync(new URL('../schema.sql', import.meta.url), 'utf8'));
  const wrap = (sql, args = []) => ({
    bind: (...a) => wrap(sql, a),
    async all() { return { results: db.prepare(sql).all(...args) }; },
    async first() { return db.prepare(sql).get(...args) ?? null; },
    async run() { const r = db.prepare(sql).run(...args); return { meta: { changes: Number(r.changes) } }; },
  });
  return {
    prepare: (sql) => wrap(sql),
    async batch(stmts) { return Promise.all(stmts.map((s) => s.run())); },
    raw: db,
  };
}

function setup() {
  const env = { DB: d1(), INGEST_TOKEN: INGEST, READ_TOKEN: READ, RETENTION_DAYS: '30' };
  const call = (method, path, { token, body } = {}) =>
    worker.fetch(
      new Request('https://x.test' + path, {
        method,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body),
      }),
      env,
    );
  return { env, call };
}

const now = Date.now();
const item = (id, over = {}) => ({
  id, package: 'com.kakao.talk', app: '카카오톡', title: '엄마', text: '저녁 먹고 들어오니?',
  subText: '', conversation: '', category: 'msg', postedAt: now - 60_000, postedAtIso: '2026-11-18T09:12:00+09:00',
  ...over,
});
const payload = (items) => ({ version: 1, device: { id: 'dev1', name: 'Galaxy S24' }, sentAt: now, items });

test('ingest 는 INGEST_TOKEN 만 받고, 같은 id 는 한 번만 저장', async () => {
  const { call } = setup();
  assert.equal((await call('POST', '/ingest', { body: payload([item('a')]) })).status, 401);
  assert.equal((await call('POST', '/ingest', { token: READ, body: payload([item('a')]) })).status, 401);

  let r = await call('POST', '/ingest', { token: INGEST, body: payload([item('a'), item('b')]) });
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { ok: true, received: 2, inserted: 2 });

  r = await call('POST', '/ingest', { token: INGEST, body: payload([item('a')]) });
  assert.deepEqual(await r.json(), { ok: true, received: 1, inserted: 0 });

  // 앱의 연결 테스트(빈 목록)
  r = await call('POST', '/ingest', { token: INGEST, body: payload([]) });
  assert.deepEqual(await r.json(), { ok: true, received: 0, inserted: 0 });
});

test('잘못된 항목은 400', async () => {
  const { call } = setup();
  const r = await call('POST', '/ingest', { token: INGEST, body: payload([{ id: 'x' }]) });
  assert.equal(r.status, 400);
});

test('REST 조회: 검색, 앱 필터, after_id 커서, 처리 표시', async () => {
  const { call } = setup();
  await call('POST', '/ingest', {
    token: INGEST,
    body: payload([
      item('1'),
      item('2', { package: 'viva.republica.toss', app: '토스', title: '입금 50,000원', text: '홍길동님이 보냈어요', postedAt: now - 30_000 }),
      item('3', { title: '프로젝트 팀방', text: '이준호: 내일 10시 회의 자료 50%_완료', postedAt: now - 10_000 }),
    ]),
  });

  assert.equal((await call('GET', '/api/notifications', { token: INGEST })).status, 401);

  let r = await (await call('GET', '/api/notifications', { token: READ })).json();
  assert.equal(r.count, 3);
  assert.equal(r.items[0].title, '프로젝트 팀방'); // 최근 것부터
  assert.equal(r.next_after_id, 3);

  r = await (await call('GET', '/api/notifications?q=' + encodeURIComponent('입금 홍길동'), { token: READ })).json();
  assert.deepEqual(r.items.map((i) => i.app), ['토스']);

  // LIKE 와일드카드(% _)는 글자 그대로 검색
  r = await (await call('GET', '/api/notifications?q=' + encodeURIComponent('50%_'), { token: READ })).json();
  assert.deepEqual(r.items.map((i) => i.id), [3]);

  r = await (await call('GET', '/api/notifications?app=' + encodeURIComponent('카카오'), { token: READ })).json();
  assert.equal(r.count, 2);

  r = await (await call('GET', '/api/notifications?after_id=1&limit=1', { token: READ })).json();
  assert.deepEqual(r.items.map((i) => i.id), [2]);
  assert.equal(r.has_more, true);
  assert.equal(r.next_after_id, 2);

  const h = await call('POST', '/api/notifications/handle', { token: READ, body: { ids: [1, 3], note: '답장함' } });
  assert.deepEqual(await h.json(), { ok: true, updated: 2 });
  r = await (await call('GET', '/api/notifications?unhandled=1', { token: READ })).json();
  assert.deepEqual(r.items.map((i) => i.id), [2]);

  const apps = await (await call('GET', '/api/apps', { token: READ })).json();
  assert.deepEqual(apps.apps.map((a) => [a.app, a.count, a.unhandled]), [['카카오톡', 2, 0], ['토스', 1, 1]]);
});

test('MCP: initialize, tools/list, tools/call, 경로 토큰', async () => {
  const { call } = setup();
  await call('POST', '/ingest', { token: INGEST, body: payload([item('1'), item('2', { title: '택배 도착' })]) });

  assert.equal((await call('POST', '/mcp', { body: { jsonrpc: '2.0', id: 1, method: 'ping' } })).status, 401);

  let r = await (await call('POST', '/mcp', {
    token: READ,
    body: { jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 't', version: '1' } } },
  })).json();
  assert.equal(r.result.protocolVersion, '2025-03-26');
  assert.ok(r.result.capabilities.tools);

  const n = await call('POST', '/mcp', { token: READ, body: { jsonrpc: '2.0', method: 'notifications/initialized' } });
  assert.equal(n.status, 202);

  r = await (await call('POST', `/mcp/${READ}`, { body: { jsonrpc: '2.0', id: 2, method: 'tools/list' } })).json();
  assert.deepEqual(r.result.tools.map((t) => t.name), ['get_new_notifications', 'list_notifications', 'list_apps', 'mark_handled']);

  r = await (await call('POST', `/mcp/${READ}`, {
    body: { jsonrpc: '2.0', id: 3, method: 'tools/call', params: { name: 'list_notifications', arguments: { query: '택배', since: '1h' } } },
  })).json();
  assert.equal(r.result.isError, false);
  const data = JSON.parse(r.result.content[0].text);
  assert.deepEqual(data.items.map((i) => i.title), ['택배 도착']);

  r = await (await call('POST', `/mcp/${READ}`, {
    body: { jsonrpc: '2.0', id: 4, method: 'tools/call', params: { name: 'mark_handled', arguments: { ids: [] } } },
  })).json();
  assert.equal(r.result.isError, true);

  r = await (await call('POST', `/mcp/wrong-token-xxxxxxxxxxxx`, { body: { jsonrpc: '2.0', id: 5, method: 'tools/list' } }));
  assert.equal(r.status, 401);

  assert.equal((await call('GET', '/mcp', { token: READ })).status, 405);
});

test('폴링: 서버가 위치를 기억해서 새 알림만 준다', async () => {
  const { call } = setup();
  const poll = async (qs = '') => (await call('GET', '/api/new' + qs, { token: READ })).json();

  // 처음엔 최근 1시간 이내에 받은 것부터
  await call('POST', '/ingest', { token: INGEST, body: payload([item('1'), item('2', { title: '택배' })]) });
  let r = await poll();
  assert.deepEqual(r.items.map((i) => i.id), [1, 2]);
  assert.equal(r.consumer, 'default');

  // 다음 호출(5분 뒤)엔 아무것도 없음
  r = await poll();
  assert.equal(r.count, 0);
  assert.equal(r.has_more, false);

  // 새 알림 3건, limit 2 → 두 번에 나눠 받는다
  await call('POST', '/ingest', { token: INGEST, body: payload([item('3'), item('4'), item('5')]) });
  r = await poll('?limit=0');
  assert.deepEqual([r.count, r.remaining], [0, 3]); // 개수만 확인, 위치는 그대로
  r = await poll('?limit=2');
  assert.deepEqual(r.items.map((i) => i.id), [3, 4]);
  assert.deepEqual([r.has_more, r.remaining], [true, 1]);
  r = await poll('?limit=2');
  assert.deepEqual(r.items.map((i) => i.id), [5]);

  // consumer 가 다르면 따로 기억
  r = await poll('?consumer=pickle');
  assert.deepEqual(r.items.map((i) => i.id), [1, 2, 3, 4, 5]);

  // peek 은 위치를 옮기지 않고, ack 로 확정
  await call('POST', '/ingest', { token: INGEST, body: payload([item('6')]) });
  r = await poll('?consumer=pickle&peek=1');
  assert.deepEqual(r.items.map((i) => i.id), [6]);
  r = await poll('?consumer=pickle&peek=1');
  assert.deepEqual(r.items.map((i) => i.id), [6]);
  const ack = await call('POST', '/api/new/ack', { token: READ, body: { consumer: 'pickle', up_to: r.cursor } });
  assert.deepEqual(await ack.json(), { ok: true, consumer: 'pickle', cursor: 6 });
  r = await poll('?consumer=pickle');
  assert.equal(r.count, 0);

  assert.equal((await call('GET', '/api/new?consumer=' + encodeURIComponent('나쁜 이름'), { token: READ })).status, 400);
});

test('처음 보는 consumer 는 1시간보다 오래된 알림은 건너뛴다', async () => {
  const { env, call } = setup();
  await call('POST', '/ingest', { token: INGEST, body: payload([item('old')]) });
  env.DB.raw.exec(`UPDATE notifications SET received_at = received_at - 7200000`);
  await call('POST', '/ingest', { token: INGEST, body: payload([item('new')]) });
  const r = await (await call('GET', '/api/new', { token: READ })).json();
  assert.equal(r.count, 1);
});

test('URL 토큰과 텍스트 형식 (헤더를 못 넣는 폴링 도구용)', async () => {
  const { call } = setup();
  await call('POST', '/ingest', { token: INGEST, body: payload([item('1', { text: '저녁\n먹고 들어오니?' })]) });
  assert.equal((await call('GET', '/api/new?token=wrong-token-xxxxxxxxxx')).status, 401);
  assert.equal((await call('GET', `/api/new?token=${INGEST}`)).status, 401);

  let res = await call('GET', `/api/new?token=${READ}&format=text&consumer=feed`);
  assert.equal(res.headers.get('Content-Type'), 'text/plain; charset=utf-8');
  assert.equal(await res.text(), '새 알림 1건 (2026-11-18 09:12 ~ 2026-11-18 09:12)\n#1 [2026-11-18 09:12] 카카오톡 | 엄마: 저녁 먹고 들어오니?\n');
  res = await call('GET', `/api/new?token=${READ}&format=text&consumer=feed`);
  assert.equal(await res.text(), '새 알림 없음\n');
});

test('하루 한 번 몰아 받기: 1000건까지, 앱별로 묶기, 긴 본문 자르기', async () => {
  const { call } = setup();
  const many = [];
  for (let i = 0; i < 300; i++) many.push(item(`k${i}`, { postedAtIso: '2026-11-18T10:00:00+09:00' }));
  many.push(item('t1', { package: 'viva.republica.toss', app: '토스', title: '입금', text: '가'.repeat(500), postedAtIso: '2026-11-18T22:30:00+09:00' }));
  await call('POST', '/ingest', { token: INGEST, body: payload(many) });

  const res = await call('GET', `/api/new?token=${READ}&consumer=grok&format=text&group=app&limit=1000`);
  const body = await res.text();
  const lines = body.trimEnd().split('\n');
  assert.equal(lines[0], '새 알림 301건 (2026-11-18 10:00 ~ 2026-11-18 22:30)');
  assert.equal(lines[2], '## 카카오톡 (300건)');
  assert.ok(lines.includes('## 토스 (1건)'));
  const toss = lines[lines.length - 1];
  assert.ok(toss.startsWith('#301 [2026-11-18 22:30] 입금: '));
  assert.ok(toss.endsWith('가…'));
  assert.equal(toss.length, '#301 [2026-11-18 22:30] 입금: '.length + 300 + 1);

  assert.equal(await (await call('GET', `/api/new?token=${READ}&consumer=grok&format=text`)).text(), '새 알림 없음\n');
});

test('상태 점검: 마지막 수신과 에이전트별 마지막 확인', async () => {
  const { call } = setup();
  let st = await (await call('GET', '/api/status', { token: READ })).json();
  assert.equal(st.notifications.total, 0);
  assert.deepEqual(st.consumers, []);

  await call('POST', '/ingest', { token: INGEST, body: payload([item('1'), item('2')]) });
  await call('GET', '/api/new?consumer=pickle&limit=1', { token: READ });
  st = await (await call('GET', '/api/status', { token: READ })).json();
  assert.equal(st.notifications.total, 2);
  assert.equal(st.notifications.minutes_since_last_received, 0);
  assert.equal(st.devices[0].name, 'Galaxy S24');
  assert.deepEqual(st.consumers.map((c) => [c.consumer, c.pending, c.minutes_since_last_check]), [['pickle', 1, 0]]);

  const t = await (await call('GET', `/api/status?format=text&token=${READ}`)).text();
  assert.match(t, /pickle: 마지막 확인 0분 전 · 안 가져간 알림 1건/);
  // 상태 확인은 커서를 움직이지 않는다
  assert.equal((await (await call('GET', '/api/new?consumer=pickle', { token: READ })).json()).count, 1);
});

test('MCP get_new_notifications', async () => {
  const { call } = setup();
  await call('POST', '/ingest', { token: INGEST, body: payload([item('1')]) });
  const tool = async (args) => {
    const r = await (await call('POST', `/mcp/${READ}`, {
      body: { jsonrpc: '2.0', id: 1, method: 'tools/call', params: { name: 'get_new_notifications', arguments: args } },
    })).json();
    return r.result.structuredContent;
  };
  assert.equal((await tool({ consumer: 'pickle' })).count, 1);
  assert.equal((await tool({ consumer: 'pickle' })).count, 0);
});

test('READ_TOKEN 여러 개: 에이전트마다 다른 토큰, 위치도 따로', async () => {
  const { env, call } = setup();
  const PICKLE = 'pickle-token-0123456789ab';
  const GROK = 'grok-token-0123456789abcd';
  env.READ_TOKEN = `${PICKLE}, ${GROK}`;
  await call('POST', '/ingest', { token: INGEST, body: payload([item('1')]) });
  const poll = async (tok, consumer) => (await call('GET', `/api/new?consumer=${consumer}&token=${tok}`)).json();
  assert.equal((await poll(PICKLE, 'pickle')).count, 1);
  assert.equal((await poll(GROK, 'grok')).count, 1); // pickle 이 가져가도 grok 은 따로 받는다
  assert.equal((await poll(GROK, 'grok')).count, 0);
  // 그록 토큰만 빼면 그록만 막힌다
  env.READ_TOKEN = PICKLE;
  assert.equal((await call('GET', `/api/new?consumer=grok&token=${GROK}`)).status, 401);
  assert.equal((await call('GET', `/api/new?consumer=pickle&token=${PICKLE}`)).status, 200);
});

test('토큰이 설정되지 않으면 아무것도 받지 않는다', async () => {
  const { env, call } = setup();
  env.INGEST_TOKEN = '';
  assert.equal((await call('POST', '/ingest', { token: '', body: payload([]) })).status, 500);
});

test('보관 기간이 지난 알림 정리', async () => {
  const { env, call } = setup();
  await call('POST', '/ingest', {
    token: INGEST,
    body: payload([item('old', { postedAt: now - 40 * 86_400_000 }), item('new')]),
  });
  await worker.scheduled({}, env);
  const r = await (await call('GET', '/api/notifications', { token: READ })).json();
  assert.equal(r.count, 1);
});

test('시간 표현 해석', () => {
  const base = Date.UTC(2026, 10, 18, 0, 0, 0);
  assert.equal(parseTime('30m', base), base - 30 * 60_000);
  assert.equal(parseTime('2d', base), base - 2 * 86_400_000);
  assert.equal(parseTime('2026-11-18T09:00:00+09:00', base), base);
  assert.equal(parseTime(String(base), base), base);
  assert.equal(parseTime('', base), null);
  assert.throws(() => parseTime('어제', base));
});
