<?php
declare(strict_types=1);

function migrationColumnExists(PDO $pdo, string $table, string $column): bool {
    $query = $pdo->prepare('SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?');
    $query->execute([$table, $column]);
    return (bool) $query->fetchColumn();
}

$pdo->exec("CREATE TABLE IF NOT EXISTS licensed_devices (id CHAR(32) PRIMARY KEY, telegram_id BIGINT UNSIGNED NOT NULL, public_key TEXT NOT NULL, key_fingerprint CHAR(64) NOT NULL, device_name VARCHAR(80) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked_at DATETIME NULL, UNIQUE KEY licensed_devices_key (key_fingerprint), INDEX licensed_devices_user (telegram_id,revoked_at)) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE IF NOT EXISTS device_activations (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, expected_user_id BIGINT UNSIGNED NOT NULL, challenge CHAR(43) NOT NULL, public_key TEXT NULL, key_fingerprint CHAR(64) NULL, device_name VARCHAR(80) NULL, proof_at DATETIME NULL, telegram_confirmed_at DATETIME NULL, completed_at DATETIME NULL, expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX device_activations_expiry (expires_at)) ENGINE=InnoDB");
if (!migrationColumnExists($pdo, 'activity_sessions', 'device_id')) {
    $pdo->exec('ALTER TABLE activity_sessions ADD device_id CHAR(32) NULL AFTER telegram_id, ADD INDEX activity_sessions_device (device_id), ADD CONSTRAINT activity_sessions_device_fk FOREIGN KEY (device_id) REFERENCES licensed_devices(id) ON DELETE SET NULL');
}
$pdo->exec('UPDATE activity_sessions SET revoked_at=UTC_TIMESTAMP() WHERE device_id IS NULL AND revoked_at IS NULL');
