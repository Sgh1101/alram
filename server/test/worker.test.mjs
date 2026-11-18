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
  assert.deepEqual(r.result.tools.map((t) => t.name), ['list_notifications', 'list_apps', 'mark_handled']);

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
