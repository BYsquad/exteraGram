<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS chat_flames (
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
) ENGINE=InnoDB');
