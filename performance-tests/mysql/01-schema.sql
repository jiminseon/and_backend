CREATE DATABASE IF NOT EXISTS andDB
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE andDB;

CREATE TABLE IF NOT EXISTS `user` (
  user_id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refreshToken (
  token_id BINARY(16) NOT NULL,
  user_id BIGINT NOT NULL,
  token VARCHAR(512) NOT NULL,
  expiry_at DATETIME(6) NOT NULL,
  PRIMARY KEY (token_id),
  KEY idx_refresh_token_user_id (user_id),
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS fcm_token (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  fcm_token VARCHAR(512) NULL,
  device_id VARCHAR(255) NULL,
  is_active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_fcm_token_user_id (user_id),
  UNIQUE KEY uk_fcm_user_device (user_id, device_id),
  CONSTRAINT fk_fcm_token_user FOREIGN KEY (user_id) REFERENCES `user` (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS company (
  company_stockcode VARCHAR(50) NOT NULL,
  name VARCHAR(255) NULL,
  PRIMARY KEY (company_stockcode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alert (
  alert_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  is_actived BIT(1) NULL DEFAULT b'1',
  title VARCHAR(255) NULL,
  stock_code VARCHAR(50) NULL,
  last_notified_at DATETIME(6) NULL,
  is_triggered BIT(1) NULL DEFAULT b'0',
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  ai_feedback TEXT NULL,
  PRIMARY KEY (alert_id),
  KEY idx_alert_stock_active (stock_code, is_actived),
  KEY idx_alert_user_stock (user_id, stock_code),
  KEY idx_alert_user_triggered (user_id, is_triggered, is_actived)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alertCondition (
  alert_condition_id BIGINT NOT NULL AUTO_INCREMENT,
  category VARCHAR(80) NULL,
  indicator VARCHAR(80) NOT NULL,
  data_scope VARCHAR(30) NULL,
  description TEXT NULL,
  created_at DATETIME(6) NULL,
  PRIMARY KEY (alert_condition_id),
  UNIQUE KEY uk_alert_condition_indicator (indicator)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alertConditionManager (
  alert_id BIGINT NOT NULL,
  alert_condition_id BIGINT NOT NULL,
  threshold DOUBLE NULL,
  threshold2 DOUBLE NULL,
  PRIMARY KEY (alert_id, alert_condition_id),
  KEY idx_acm_condition_id (alert_condition_id),
  CONSTRAINT fk_acm_alert FOREIGN KEY (alert_id) REFERENCES alert (alert_id) ON DELETE CASCADE,
  CONSTRAINT fk_acm_alert_condition FOREIGN KEY (alert_condition_id) REFERENCES alertCondition (alert_condition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alertPrice (
  price_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  stock_code VARCHAR(50) NULL,
  is_price BIT(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (price_id),
  UNIQUE KEY uk_alert_price_user_stock (user_id, stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alertHistory (
  alertHistory_id BIGINT NOT NULL AUTO_INCREMENT,
  alert_id BIGINT NOT NULL,
  indicator_snapshot TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (alertHistory_id),
  KEY idx_alert_history_alert_id (alert_id),
  KEY idx_alert_history_created_at (created_at),
  CONSTRAINT fk_alert_history_alert FOREIGN KEY (alert_id) REFERENCES alert (alert_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS preset (
  preset_id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NULL,
  category VARCHAR(80) NULL,
  created_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL,
  PRIMARY KEY (preset_id),
  KEY idx_preset_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS presetCondition (
  preset_id BIGINT NOT NULL,
  alert_condition_id BIGINT NOT NULL,
  threshold DOUBLE NULL,
  threshold2 DOUBLE NULL,
  PRIMARY KEY (preset_id, alert_condition_id),
  KEY idx_pc_condition_id (alert_condition_id),
  CONSTRAINT fk_pc_preset FOREIGN KEY (preset_id) REFERENCES preset (preset_id) ON DELETE CASCADE,
  CONSTRAINT fk_pc_alert_condition FOREIGN KEY (alert_condition_id) REFERENCES alertCondition (alert_condition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS condition_base (
  id BIGINT NOT NULL AUTO_INCREMENT,
  alert_id BIGINT NOT NULL,
  stock_code VARCHAR(20) NOT NULL,
  base_value DOUBLE NULL,
  created_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_condition_base_alert_stock (alert_id, stock_code),
  KEY idx_condition_base_stock (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS condition_search (
  alert_id BIGINT NOT NULL,
  stock_code VARCHAR(50) NOT NULL,
  is_triggered BIT(1) NULL DEFAULT b'0',
  trigger_date DATETIME(6) NULL,
  PRIMARY KEY (alert_id, stock_code),
  CONSTRAINT fk_condition_search_alert FOREIGN KEY (alert_id) REFERENCES alert (alert_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS condition_search_result (
  id BIGINT NOT NULL AUTO_INCREMENT,
  alert_id BIGINT NOT NULL,
  stock_code VARCHAR(50) NOT NULL,
  is_triggered BIT(1) NULL DEFAULT b'0',
  trigger_date DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY idx_csr_alert_id (alert_id),
  KEY idx_csr_stock_code (stock_code),
  CONSTRAINT fk_csr_alert FOREIGN KEY (alert_id) REFERENCES alert (alert_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS daily_candle (
  stock_code VARCHAR(50) NOT NULL,
  date DATETIME(6) NOT NULL,
  open_price DOUBLE NULL,
  close_price DOUBLE NULL,
  high_price DOUBLE NULL,
  low_price DOUBLE NULL,
  volume INT NULL,
  rsi_14 DOUBLE NULL,
  bb_upper DOUBLE NULL,
  bb_lower DOUBLE NULL,
  sma_5 DOUBLE NULL,
  sma_10 DOUBLE NULL,
  sma_20 DOUBLE NULL,
  sma_30 DOUBLE NULL,
  sma_50 DOUBLE NULL,
  sma_100 DOUBLE NULL,
  sma_200 DOUBLE NULL,
  PRIMARY KEY (stock_code, date),
  KEY idx_daily_candle_stock_date (stock_code, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS minuteCandle (
  stock_code VARCHAR(50) NOT NULL,
  date DATETIME(6) NOT NULL,
  open_price DOUBLE NULL,
  close_price DOUBLE NULL,
  high_price DOUBLE NULL,
  low_price DOUBLE NULL,
  volume INT NULL,
  PRIMARY KEY (stock_code, date),
  KEY idx_minute_candle_stock_date (stock_code, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
