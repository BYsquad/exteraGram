<?php
declare(strict_types=1);

function reportClientDevice(PDO $pdo, array $request): void {
    $id = $request['device_id'] ?? '';
    $model = $request['model'] ?? '';
    $version = $request['version'] ?? '';
    $android = $request['android_api'] ?? 0;
    if (!is_string($id) || !preg_match('/^[a-f0-9]{64}$/D', $id)
        || !is_string($model) || trim($model) === ''
        || !is_string($version) || !preg_match('/^[0-9A-Za-z._-]{1,24}$/D', $version)
        || !is_int($android) || $android < 24 || $android > 255) {
        throw new InvalidArgumentException('Invalid device report');
    }
    $pdo->prepare('INSERT INTO client_devices (device_id,model,app_version,android_api,first_seen_at,last_seen_at)
        VALUES (?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE model=VALUES(model),
        app_version=VALUES(app_version),android_api=VALUES(android_api),last_seen_at=UTC_TIMESTAMP()')
        ->execute([$id, mb_substr(trim($model), 0, 80), $version, $android]);
}

function clientDeviceCount(PDO $pdo): int {
    return (int) $pdo->query('SELECT COUNT(*) FROM client_devices')->fetchColumn();
}

function adminClientDevices(PDO $pdo, mixed $requestedPage): array {
    $total = clientDeviceCount($pdo);
    $pageSize = 40;
    $page = max(1, filter_var($requestedPage, FILTER_VALIDATE_INT) ?: 1);
    $page = min($page, max(1, (int) ceil($total / $pageSize)));
    $offset = ($page - 1) * $pageSize;
    $rows = $pdo->query("SELECT LEFT(device_id,16) AS device, model, app_version, android_api, first_seen_at, last_seen_at
        FROM client_devices ORDER BY last_seen_at DESC, device_id DESC LIMIT $pageSize OFFSET $offset")->fetchAll();
    return ['rows' => $rows, 'total' => $total, 'page' => $page, 'page_size' => $pageSize];
}
