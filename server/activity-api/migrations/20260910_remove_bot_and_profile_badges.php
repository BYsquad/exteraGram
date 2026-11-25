<?php
declare(strict_types=1);

$pdo->exec("CREATE TABLE IF NOT EXISTS users (telegram_id BIGINT UNSIGNED PRIMARY KEY, username VARCHAR(64) NULL, first_name VARCHAR(128) NULL, last_name VARCHAR(128) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE IF NOT EXISTS admins (telegram_id BIGINT UNSIGNED PRIMARY KEY, added_by BIGINT UNSIGNED NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
if ($pdo->query("SHOW TABLES LIKE 'bot_users'")->fetchColumn()) {
    $pdo->exec('INSERT IGNORE INTO users SELECT telegram_id,username,first_name,last_name,created_at,last_seen_at FROM bot_users');
}
if ($pdo->query("SHOW TABLES LIKE 'bot_admins'")->fetchColumn()) {
    $pdo->exec('INSERT IGNORE INTO admins SELECT telegram_id,added_by,created_at FROM bot_admins');
}
