<?php
declare(strict_types=1);

$column = $pdo->query("SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='chat_activity' AND COLUMN_NAME='peer_kind'")->fetchColumn();
if (!$column) $pdo->exec("ALTER TABLE chat_activity ADD peer_kind TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER peer_name");
