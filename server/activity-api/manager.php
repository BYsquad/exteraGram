<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

try {
    $username = ltrim(trim(appSetting('manager_username')), '@');
    if (!preg_match('/^[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]$/D', $username)) throw new RuntimeException('Manager is unavailable');
    header('Location: https://t.me/' . rawurlencode($username), true, 302);
} catch (Throwable) {
    http_response_code(503);
    header('Content-Type: text/plain; charset=utf-8');
    echo 'Контакт менеджера временно недоступен';
}
