<?php
declare(strict_types=1);

$username = ltrim(trim((string) (appConfig()['manager_username'] ?? 'BYgram_manager')), '@');
$pdo->prepare('INSERT IGNORE INTO app_settings (setting_key,setting_value) VALUES (?,?)')->execute(['manager_username', $username]);
