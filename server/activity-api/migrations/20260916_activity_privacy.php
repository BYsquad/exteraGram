<?php
declare(strict_types=1);

$columnExists = static function (string $table, string $column) use ($pdo): bool {
    $query = $pdo->prepare('SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?');
    $query->execute([$table, $column]);
    return (bool) $query->fetchColumn();
};

if (!$columnExists('chat_activity', 'activity_type')) {
    $pdo->exec('ALTER TABLE chat_activity ADD activity_type TINYINT UNSIGNED NOT NULL DEFAULT 4 AFTER owner_id');
}
$pdo->exec("ALTER TABLE chat_activity MODIFY peer_id BIGINT NULL, MODIFY peer_name VARCHAR(100) NOT NULL DEFAULT ''");
if (!$columnExists('activity_viewers', 'permissions')) {
    $pdo->exec('ALTER TABLE activity_viewers ADD permissions TINYINT UNSIGNED NOT NULL DEFAULT 4 AFTER viewer_id');
}
