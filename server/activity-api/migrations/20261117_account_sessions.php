<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS account_login_attempts (
    login_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    exchange_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    telegram_id BIGINT UNSIGNED NOT NULL,
    expires_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    INDEX account_login_expiry (expires_at)
) ENGINE=InnoDB;');

if (!$pdo->query("SHOW COLUMNS FROM activity_sessions LIKE 'account_confirmed'")->fetch()) {
    $pdo->exec('ALTER TABLE activity_sessions ADD COLUMN account_confirmed TINYINT(1) NOT NULL DEFAULT 0');
}
