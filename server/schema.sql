-- Alram Mail 에이전트 서버 스키마 (Cloudflare D1 / SQLite)
CREATE TABLE IF NOT EXISTS notifications (
  seq           INTEGER PRIMARY KEY AUTOINCREMENT, -- 에이전트가 쓰는 번호 (커서로도 쓴다)
  uid           TEXT NOT NULL UNIQUE,              -- 폰이 붙인 고유 키 (재전송 중복 방지)
  device_id     TEXT NOT NULL,
  device_name   TEXT NOT NULL DEFAULT '',
  package       TEXT NOT NULL,
  app           TEXT NOT NULL,
  title         TEXT NOT NULL DEFAULT '',
  text          TEXT NOT NULL DEFAULT '',
  sub_text      TEXT NOT NULL DEFAULT '',
  conversation  TEXT NOT NULL DEFAULT '',
  category      TEXT NOT NULL DEFAULT '',
  posted_at     INTEGER NOT NULL,                  -- ms
  posted_at_iso TEXT NOT NULL DEFAULT '',          -- 폰 시간대 기준 ISO-8601
  received_at   INTEGER NOT NULL,
  handled_at    INTEGER,                           -- 에이전트가 처리했다고 표시한 시각
  note          TEXT                               -- 처리하면서 남긴 메모
);
CREATE INDEX IF NOT EXISTS idx_notifications_posted ON notifications (posted_at);
CREATE INDEX IF NOT EXISTS idx_notifications_package ON notifications (package, posted_at);
CREATE INDEX IF NOT EXISTS idx_notifications_handled ON notifications (handled_at, posted_at);

-- 폴링하는 에이전트마다 "어디까지 가져갔는지" 기억한다. 에이전트가 상태를 못 들고 다녀도 새 알림만 받을 수 있다.
CREATE TABLE IF NOT EXISTS cursors (
  consumer   TEXT PRIMARY KEY,
  last_seq   INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
