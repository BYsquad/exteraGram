<?php
declare(strict_types=1);

$pdo->exec('CREATE TABLE IF NOT EXISTS profile_view_settings (telegram_id BIGINT UNSIGNED PRIMARY KEY, enabled TINYINT(1) NOT NULL DEFAULT 0) ENGINE=InnoDB');
$pdo->exec("CREATE TABLE IF NOT EXISTS profile_views (owner_id BIGINT UNSIGNED NOT NULL, viewer_id BIGINT UNSIGNED NOT NULL, viewer_name VARCHAR(128) NOT NULL, viewer_username VARCHAR(64) NOT NULL DEFAULT '', visited_at DATETIME NOT NULL, notified_at DATETIME NULL, seen_at DATETIME NULL, PRIMARY KEY (owner_id,viewer_id), INDEX profile_views_expiry (visited_at), INDEX profile_views_viewer (viewer_id)) ENGINE=InnoDB");
