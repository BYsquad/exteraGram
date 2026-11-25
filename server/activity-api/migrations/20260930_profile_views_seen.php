<?php
declare(strict_types=1);

$column = $pdo->query("SHOW COLUMNS FROM profile_views LIKE 'seen_at'")->fetch();
if (!$column) $pdo->exec('ALTER TABLE profile_views ADD COLUMN seen_at DATETIME NULL AFTER notified_at');
