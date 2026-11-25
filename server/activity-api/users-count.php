<?php
declare(strict_types=1);

require __DIR__ . '/bootstrap.php';
require __DIR__ . '/client-devices.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') jsonResponse(['ok' => false], 405);
try {
    $count = clientDeviceCount(db());
    header('Cache-Control: public, max-age=60');
    jsonResponse(['count' => $count]);
} catch (Throwable $error) {
    error_log('BYgram device counter failure: ' . get_class($error));
    jsonResponse(['ok' => false], 503);
}
