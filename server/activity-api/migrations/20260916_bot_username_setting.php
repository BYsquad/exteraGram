<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS app_settings (setting_key VARCHAR(64) PRIMARY KEY, setting_value VARCHAR(255) NOT NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB');
$username = ltrim(trim((string) (appConfig()['bot_username'] ?? 'BYgram_unofficial_client_bot')), '@');
$pdo->prepare('INSERT IGNORE INTO app_settings (setting_key,setting_value) VALUES (?,?)')->execute(['bot_username', $username]);
