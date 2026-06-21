USE andDB;

CREATE TABLE IF NOT EXISTS load_seed_numbers (
  n INT NOT NULL PRIMARY KEY
) ENGINE=Memory;

INSERT IGNORE INTO load_seed_numbers (n)
WITH RECURSIVE seq AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM seq WHERE n < 200
)
SELECT n FROM seq;

INSERT IGNORE INTO company (company_stockcode, name) VALUES
  ('005930', 'Samsung Electronics'),
  ('000660', 'SK Hynix'),
  ('035420', 'NAVER'),
  ('035720', 'Kakao'),
  ('005380', 'Hyundai Motor');

INSERT IGNORE INTO alertCondition
  (alert_condition_id, category, indicator, data_scope, description, created_at)
VALUES
  (1, 'price', 'PRICE_ABOVE', 'minute', 'Current price is above threshold', NOW(6)),
  (2, 'price', 'PRICE_BELOW', 'minute', 'Current price is below threshold', NOW(6)),
  (3, 'rsi_alert', 'RSI_UNDER', 'daily', 'RSI is below threshold', NOW(6)),
  (4, 'sma_alert', 'SMA_20_UP', 'daily', 'SMA20 is above threshold', NOW(6)),
  (5, 'volume_alert', 'VOLUME_CHANGE_PERCENT_UP', 'minute', 'Volume ratio is above threshold', NOW(6)),
  (6, 'bollinger_alert', 'BOLLINGER_UPPER_TOUCH', 'daily', 'Price touches upper Bollinger band', NOW(6));

INSERT IGNORE INTO `user` (user_id, email, password, name, created_at, updated_at)
SELECT
  n,
  CONCAT('load-user-', n, '@and.test'),
  '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiYwPXSKrNKO6DeI5j9P4p4LcVY9zFa',
  CONCAT('load-user-', n),
  NOW(6),
  NOW(6)
FROM load_seed_numbers
WHERE n <= 100;

INSERT IGNORE INTO fcm_token (id, user_id, fcm_token, device_id, is_active, created_at, updated_at)
SELECT
  ((u.n - 1) * 5) + d.n,
  u.n,
  CONCAT('load-test-fcm-token-', u.n, '-', d.n),
  CONCAT('load-device-', d.n),
  b'1',
  NOW(6),
  NOW(6)
FROM load_seed_numbers u
JOIN load_seed_numbers d ON d.n <= 5
WHERE u.n <= 100;

INSERT IGNORE INTO alert
  (alert_id, user_id, is_actived, title, stock_code, last_notified_at, is_triggered, created_at, updated_at, ai_feedback)
SELECT
  (u.n * 100000) + (c.company_no * 1000) + a.alert_no,
  u.n,
  b'1',
  CONCAT(c.stock_code, ' load alert ', a.alert_no),
  c.stock_code,
  NULL,
  b'0',
  NOW(6),
  NOW(6),
  'load-test seed alert'
FROM load_seed_numbers u
JOIN (
  SELECT 1 AS company_no, '005930' AS stock_code
  UNION ALL SELECT 2, '000660'
  UNION ALL SELECT 3, '035420'
  UNION ALL SELECT 4, '035720'
  UNION ALL SELECT 5, '005380'
) c
JOIN (
  SELECT 1 AS alert_no
  UNION ALL SELECT 2
  UNION ALL SELECT 3
  UNION ALL SELECT 4
) a
WHERE u.n <= 100;

INSERT IGNORE INTO alertConditionManager (alert_id, alert_condition_id, threshold, threshold2)
SELECT
  (u.n * 100000) + (c.company_no * 1000) + a.alert_no,
  CASE a.alert_no
    WHEN 1 THEN 1
    WHEN 2 THEN 3
    WHEN 3 THEN 4
    ELSE 5
  END,
  CASE a.alert_no
    WHEN 1 THEN c.price_threshold
    WHEN 2 THEN 45
    WHEN 3 THEN c.sma_threshold
    ELSE 120
  END,
  NULL
FROM load_seed_numbers u
JOIN (
  SELECT 1 AS company_no, '005930' AS stock_code, 70000 AS price_threshold, 68000 AS sma_threshold
  UNION ALL SELECT 2, '000660', 180000, 170000
  UNION ALL SELECT 3, '035420', 190000, 185000
  UNION ALL SELECT 4, '035720', 60000, 55000
  UNION ALL SELECT 5, '005380', 250000, 240000
) c
JOIN (
  SELECT 1 AS alert_no
  UNION ALL SELECT 2
  UNION ALL SELECT 3
  UNION ALL SELECT 4
) a
WHERE u.n <= 100;

INSERT IGNORE INTO alertPrice (price_id, user_id, stock_code, is_price)
SELECT
  ((u.n - 1) * 5) + c.company_no,
  u.n,
  c.stock_code,
  b'1'
FROM load_seed_numbers u
JOIN (
  SELECT 1 AS company_no, '005930' AS stock_code
  UNION ALL SELECT 2, '000660'
  UNION ALL SELECT 3, '035420'
  UNION ALL SELECT 4, '035720'
  UNION ALL SELECT 5, '005380'
) c
WHERE u.n <= 100;

INSERT IGNORE INTO preset (preset_id, user_id, title, category, created_at, updated_at)
SELECT
  u.n,
  u.n,
  'Load Test Basic Preset',
  'portfolio',
  NOW(6),
  NOW(6)
FROM load_seed_numbers u
WHERE u.n <= 100;

INSERT IGNORE INTO presetCondition (preset_id, alert_condition_id, threshold, threshold2)
SELECT u.n, 1, 70000, NULL
FROM load_seed_numbers u
WHERE u.n <= 100;

INSERT IGNORE INTO condition_base (alert_id, stock_code, base_value)
SELECT
  (u.n * 100000) + (c.company_no * 1000) + 1,
  c.stock_code,
  c.base_price
FROM load_seed_numbers u
JOIN (
  SELECT 1 AS company_no, '005930' AS stock_code, 69000 AS base_price
  UNION ALL SELECT 2, '000660', 175000
  UNION ALL SELECT 3, '035420', 188000
  UNION ALL SELECT 4, '035720', 57000
  UNION ALL SELECT 5, '005380', 245000
) c
WHERE u.n <= 100;

INSERT IGNORE INTO daily_candle
  (stock_code, date, open_price, close_price, high_price, low_price, volume,
   rsi_14, bb_upper, bb_lower, sma_5, sma_10, sma_20, sma_30, sma_50, sma_100, sma_200)
SELECT
  c.stock_code,
  DATE_SUB(CURRENT_DATE(), INTERVAL d.n DAY),
  c.base_price - 500 + d.n,
  c.base_price + d.n,
  c.base_price + 3000,
  c.base_price - 3000,
  c.base_volume + (d.n * 1000),
  35 + MOD(d.n, 25),
  c.base_price + 2500,
  c.base_price - 2500,
  c.base_price - 200 + d.n,
  c.base_price - 150 + d.n,
  c.base_price - 100 + d.n,
  c.base_price - 80 + d.n,
  c.base_price - 50 + d.n,
  c.base_price - 30 + d.n,
  c.base_price - 10 + d.n
FROM load_seed_numbers d
JOIN (
  SELECT '005930' AS stock_code, 70500 AS base_price, 15000000 AS base_volume
  UNION ALL SELECT '000660', 181000, 8000000
  UNION ALL SELECT '035420', 191000, 2500000
  UNION ALL SELECT '035720', 61000, 4000000
  UNION ALL SELECT '005380', 252000, 1200000
) c
WHERE d.n <= 60;

INSERT IGNORE INTO minuteCandle
  (stock_code, date, open_price, close_price, high_price, low_price, volume)
SELECT
  c.stock_code,
  DATE_ADD(CURRENT_DATE(), INTERVAL (m.n + 540) MINUTE),
  c.base_price - 300,
  c.base_price + m.n,
  c.base_price + 800,
  c.base_price - 800,
  c.base_volume + (m.n * 100)
FROM load_seed_numbers m
JOIN (
  SELECT '005930' AS stock_code, 70500 AS base_price, 120000 AS base_volume
  UNION ALL SELECT '000660', 181000, 90000
  UNION ALL SELECT '035420', 191000, 45000
  UNION ALL SELECT '035720', 61000, 60000
  UNION ALL SELECT '005380', 252000, 30000
) c
WHERE m.n <= 120;
