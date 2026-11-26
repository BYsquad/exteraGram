CREATE TABLE IF NOT EXISTS users (telegram_id BIGINT UNSIGNED PRIMARY KEY, username VARCHAR(64) NULL, first_name VARCHAR(128) NULL, last_name VARCHAR(128) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS admins (telegram_id BIGINT UNSIGNED PRIMARY KEY, added_by BIGINT UNSIGNED NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS app_settings (setting_key VARCHAR(64) PRIMARY KEY, setting_value VARCHAR(255) NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB;
INSERT IGNORE INTO app_settings (setting_key,setting_value) VALUES ('bot_username','BYgram_unofficial_client_bot');
INSERT IGNORE INTO app_settings (setting_key,setting_value) VALUES ('manager_username','BYgram_manager');
CREATE TABLE IF NOT EXISTS payment_methods (code ENUM('stars','crypto','manual') PRIMARY KEY, title VARCHAR(80) NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 0, price DECIMAL(18,2) NOT NULL DEFAULT 0, currency VARCHAR(8) NOT NULL) ENGINE=InnoDB;
INSERT IGNORE INTO payment_methods (code,title,enabled,price,currency) VALUES ('stars','Telegram Stars',0,0,'XTR'),('crypto','Crypto Bot',0,0,'BYN'),('manual','По реквизитам',0,0,'BYN');
CREATE TABLE IF NOT EXISTS promo_codes (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, discount_percent TINYINT UNSIGNED NOT NULL, usage_limit INT UNSIGNED NULL, used_count INT UNSIGNED NOT NULL DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1, expires_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, CHECK (discount_percent <= 100)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS orders (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, buyer_id BIGINT UNSIGNED NOT NULL, beneficiary_id BIGINT UNSIGNED NOT NULL, payment_method ENUM('stars','crypto','manual','promo') NOT NULL, amount DECIMAL(18,2) NOT NULL, currency VARCHAR(8) NOT NULL, promo_id BIGINT UNSIGNED NULL, status ENUM('pending','review','paid','approved','rejected','expired','refunded') NOT NULL DEFAULT 'pending', external_id VARCHAR(255) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, paid_at DATETIME NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY orders_external (payment_method, external_id), INDEX orders_buyer (buyer_id, created_at), INDEX orders_beneficiary (beneficiary_id, created_at), FOREIGN KEY (promo_id) REFERENCES promo_codes(id) ON DELETE SET NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS bank_requisites (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, bank ENUM('belarusbank','mtbank') NOT NULL, card_number VARCHAR(40) NOT NULL, holder_name VARCHAR(160) NULL, active TINYINT(1) NOT NULL DEFAULT 1, last_used_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX requisites_bank_active_used (bank, active, last_used_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS licensed_devices (id CHAR(32) PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, public_key TEXT NOT NULL, account_confirmed TINYINT(1) NOT NULL DEFAULT 1, key_fingerprint CHAR(64) NOT NULL, device_name VARCHAR(80) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked_at DATETIME NULL, UNIQUE KEY licensed_devices_key (key_fingerprint), INDEX licensed_devices_user (telegram_id,revoked_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS device_activations (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, expected_user_id BIGINT UNSIGNED NOT NULL, challenge CHAR(43) NOT NULL, public_key TEXT NULL, key_fingerprint CHAR(64) NULL, device_name VARCHAR(80) NULL, proof_at DATETIME NULL, telegram_confirmed_at DATETIME NULL, completed_at DATETIME NULL, expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX device_activations_expiry (expires_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS activity_sessions (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, device_id CHAR(32) NULL, account_confirmed TINYINT(1) NOT NULL DEFAULT 0, access_hash CHAR(64) NOT NULL UNIQUE, refresh_hash CHAR(64) NOT NULL UNIQUE, access_expires_at DATETIME NOT NULL, refresh_expires_at DATETIME NOT NULL, revoked_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX activity_sessions_user (telegram_id), INDEX activity_sessions_device (device_id), INDEX activity_sessions_expiry (access_expires_at,refresh_expires_at), FOREIGN KEY (device_id) REFERENCES licensed_devices(id) ON DELETE SET NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS api_nonces (session_id BIGINT UNSIGNED NOT NULL, nonce CHAR(36) NOT NULL, expires_at DATETIME NOT NULL, PRIMARY KEY (session_id,nonce), INDEX api_nonces_expiry (expires_at), FOREIGN KEY (session_id) REFERENCES activity_sessions(id) ON DELETE CASCADE) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS api_rate_limits (rate_key VARCHAR(191) PRIMARY KEY, window_started DATETIME NOT NULL, request_count INT UNSIGNED NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS chat_activity (owner_id BIGINT UNSIGNED PRIMARY KEY, activity_type TINYINT UNSIGNED NOT NULL DEFAULT 4, peer_id BIGINT NULL, peer_name VARCHAR(100) NOT NULL DEFAULT '', peer_kind TINYINT UNSIGNED NOT NULL DEFAULT 0, expires_at DATETIME NOT NULL, INDEX chat_activity_expiry (expires_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS activity_viewers (owner_id BIGINT UNSIGNED NOT NULL, viewer_id BIGINT UNSIGNED NOT NULL, permissions TINYINT UNSIGNED NOT NULL DEFAULT 4, PRIMARY KEY (owner_id,viewer_id), INDEX activity_viewers_viewer (viewer_id)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS typing_activations (token_hash CHAR(64) PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, public_key TEXT NOT NULL, confirmed_at DATETIME NULL, expires_at DATETIME NOT NULL) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS typing_devices (telegram_id BIGINT UNSIGNED NOT NULL, key_fingerprint CHAR(64) NOT NULL, public_key TEXT NOT NULL, account_confirmed TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (telegram_id,key_fingerprint)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS typing_nonces (telegram_id BIGINT UNSIGNED NOT NULL, nonce CHAR(36) NOT NULL, expires_at DATETIME NOT NULL, PRIMARY KEY (telegram_id,nonce), INDEX typing_nonces_expiry (expires_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS profile_view_settings (telegram_id BIGINT UNSIGNED PRIMARY KEY, enabled TINYINT(1) NOT NULL DEFAULT 0) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS profile_views (owner_id BIGINT UNSIGNED NOT NULL, viewer_id BIGINT UNSIGNED NOT NULL, viewer_name VARCHAR(128) NOT NULL, viewer_username VARCHAR(64) NOT NULL DEFAULT '', visited_at DATETIME NOT NULL, notified_at DATETIME NULL, seen_at DATETIME NULL, PRIMARY KEY (owner_id,viewer_id), INDEX profile_views_expiry (visited_at), INDEX profile_views_viewer (viewer_id)) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS referral_settings (id TINYINT UNSIGNED PRIMARY KEY, enabled TINYINT(1) NOT NULL DEFAULT 0, percent DECIMAL(5,2) NOT NULL DEFAULT 10.00, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CHECK (percent >= 0 AND percent <= 100)) ENGINE=InnoDB;
INSERT IGNORE INTO referral_settings (id) VALUES (1);
CREATE TABLE IF NOT EXISTS referrals (referred_id BIGINT UNSIGNED PRIMARY KEY, referrer_id BIGINT UNSIGNED NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX referrals_referrer (referrer_id,created_at)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS referral_earnings (order_id BIGINT UNSIGNED PRIMARY KEY, referrer_id BIGINT UNSIGNED NOT NULL, referred_id BIGINT UNSIGNED NOT NULL, amount DECIMAL(18,2) NOT NULL, percent DECIMAL(5,2) NOT NULL, commission DECIMAL(18,2) NOT NULL, currency VARCHAR(8) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX referral_earnings_referrer (referrer_id,created_at), FOREIGN KEY (order_id) REFERENCES orders(id)) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS referral_adjustments (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, amount DECIMAL(18,2) NOT NULL, currency VARCHAR(8) NOT NULL, note VARCHAR(255) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX referral_adjustments_user (telegram_id,created_at)) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS client_devices (
    device_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    model VARCHAR(80) NOT NULL,
    app_version VARCHAR(24) NOT NULL,
    android_api TINYINT UNSIGNED NOT NULL,
    first_seen_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    INDEX client_devices_last_seen (last_seen_at, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS chat_flames (
    user_low BIGINT UNSIGNED NOT NULL,
    user_high BIGINT UNSIGNED NOT NULL,
    last_day INT UNSIGNED NOT NULL DEFAULT 0,
    days INT UNSIGNED NOT NULL DEFAULT 0,
    lost_day INT UNSIGNED NOT NULL DEFAULT 0,
    lost_days INT UNSIGNED NOT NULL DEFAULT 0,
    restore_month INT UNSIGNED NOT NULL DEFAULT 0,
    restores TINYINT UNSIGNED NOT NULL DEFAULT 0,
    revision INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (user_low, user_high)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS account_login_attempts (
    login_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    exchange_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    telegram_id BIGINT UNSIGNED NOT NULL,
    expires_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    INDEX account_login_expiry (expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS profile_backgrounds (telegram_id BIGINT UNSIGNED PRIMARY KEY, image_data MEDIUMBLOB NOT NULL, mime VARCHAR(32) NOT NULL, revision CHAR(64) NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB;
