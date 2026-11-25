<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS client_devices (
    device_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    model VARCHAR(80) NOT NULL,
    app_version VARCHAR(24) NOT NULL,
    android_api TINYINT UNSIGNED NOT NULL,
    first_seen_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL,
    INDEX client_devices_last_seen (last_seen_at, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
