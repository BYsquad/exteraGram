<?php
declare(strict_types=1);
foreach (['licensed_devices', 'typing_devices'] as $table) {
    $column = $pdo->prepare('SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?');
    $column->execute([$table, 'account_confirmed']);
    if (!$column->fetchColumn()) $pdo->exec("ALTER TABLE `$table` ADD account_confirmed TINYINT(1) NOT NULL DEFAULT 1");
}
