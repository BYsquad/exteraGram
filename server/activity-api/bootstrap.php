<?php
declare(strict_types=1);

function appConfig(): array { static $config; return $config ??= require __DIR__ . '/config.php'; }
function db(): PDO {
    static $pdo; $c = appConfig();
    return $pdo ??= new PDO($c['db_dsn'], $c['db_user'], $c['db_password'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
}
function appSetting(string $key, string $default = ''): string {
    $query = db()->prepare('SELECT setting_value FROM app_settings WHERE setting_key=?');
    $query->execute([$key]);
    $value = $query->fetchColumn();
    return $value === false ? $default : (string) $value;
}
function saveAppSetting(string $key, string $value): void {
    db()->prepare('INSERT INTO app_settings (setting_key,setting_value) VALUES (?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)')->execute([$key, $value]);
}
function isOwner(int $id): bool { return $id === (int) (appConfig()['owner_id'] ?? 0); }
function isAdmin(int $id): bool {
    if (isOwner($id)) return true;
    try { $q=db()->prepare('SELECT 1 FROM admins WHERE telegram_id=?'); $q->execute([$id]); return (bool)$q->fetchColumn(); }
    catch (Throwable) { return false; }
}
final class AdminAuthenticationException extends RuntimeException {}
function telegramLoginHash(array $values, string $token): string {
    unset($values['hash']);
    ksort($values);
    $pairs = [];
    foreach ($values as $key => $value) {
        if (!is_scalar($value)) throw new AdminAuthenticationException('Недействительная сессия Telegram');
        $pairs[] = $key . '=' . $value;
    }
    return hash_hmac('sha256', implode("\n", $pairs), hash('sha256', $token, true));
}
function validateTelegramLogin(array $values, ?string $token = null): int {
    $hash = (string) ($values['hash'] ?? '');
    $authDate = (int) ($values['auth_date'] ?? 0);
    $id = (int) ($values['id'] ?? 0);
    $token ??= (string) (appConfig()['bot_token'] ?? '');
    if ($hash === '' || $token === '' || $id < 1 || abs(time() - $authDate) > 600
            || !hash_equals(telegramLoginHash($values, $token), $hash) || !isAdmin($id)) {
        throw new AdminAuthenticationException('Недействительная сессия Telegram');
    }
    return $id;
}
function startAdminSession(): void {
    if (session_status() === PHP_SESSION_ACTIVE) return;
    $path = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? '/'))), '/') . '/';
    session_name('bygram_admin');
    session_set_cookie_params(['lifetime' => 0, 'path' => $path, 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
    ini_set('session.use_strict_mode', '1');
    session_start();
}
function adminSessionUser(): int {
    startAdminSession();
    $id = (int) ($_SESSION['telegram_id'] ?? 0);
    if ($id > 0 && isAdmin($id)) return $id;
    unset($_SESSION['telegram_id']);
    return 0;
}
function requireAdminSession(): int {
    $id = adminSessionUser();
    if ($id < 1) throw new AdminAuthenticationException('Требуется вход через Telegram');
    return $id;
}
function adminCsrfToken(): string {
    return $_SESSION['admin_csrf'] ??= bin2hex(random_bytes(32));
}
function requireAdminCsrf(): void {
    $expected = $_SESSION['admin_csrf'] ?? '';
    if ($expected === '' || !hash_equals($expected, (string) ($_SERVER['HTTP_X_BYGRAM_CSRF'] ?? ''))) {
        jsonResponse(['ok' => false, 'error' => 'Обновите страницу и повторите действие'], 403);
    }
}
function jsonResponse(mixed $data, int $status = 200): never { http_response_code($status); header('Content-Type: application/json; charset=utf-8'); echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); exit; }
function loadBadges(): array {
    $badges = json_decode((string) @file_get_contents(__DIR__ . '/badges.json'), true);
    return is_array($badges) ? $badges : [];
}
function badgeKey(string $type, int $id): string {
    if (!in_array($type, ['user', 'chat'], true) || $id < 1) throw new InvalidArgumentException('Неверный тип или ID');
    return $type . ':' . $id;
}
function badgeId(string $type, mixed $value): int {
    $text = trim((string) $value);
    if ($type === 'chat' && str_starts_with($text, '-100')) $text = substr($text, 4);
    elseif ($type === 'chat' && str_starts_with($text, '-')) $text = substr($text, 1);
    if (!preg_match('/^[1-9][0-9]{0,18}$/D', $text)) throw new InvalidArgumentException('Неверный Telegram ID');
    return (int) $text;
}
function badgeFor(array $badges, string $type, int $id): bool {
    return !empty($badges[badgeKey($type, $id)]) || ($type === 'user' && !empty($badges[(string) $id]['bygram']));
}
function saveBadges(array $badges): void {
    ksort($badges, SORT_STRING);
    $json = json_encode($badges, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
    if ($json === false || file_put_contents(__DIR__ . '/badges.json.tmp', $json . "\n", LOCK_EX) === false
            || !rename(__DIR__ . '/badges.json.tmp', __DIR__ . '/badges.json')) {
        throw new RuntimeException('Не удалось сохранить значки');
    }
}
