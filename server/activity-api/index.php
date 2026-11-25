<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/account-sessions.php';

set_exception_handler(static function (Throwable $error): never {
    error_log('BYgram API failure type=' . get_class($error) . ' action=' . ($GLOBALS['bygram_action'] ?? 'unknown'));
    jsonResponse(['ok' => false], 503);
});

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') jsonResponse(['ok' => false], 405);
if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 14000000) jsonResponse(['ok' => false], 413);
$raw = (string) file_get_contents('php://input', false, null, 0, 14000001);
if (strlen($raw) > 14000000 || !is_array($request = json_decode($raw, true, 32))) jsonResponse(['ok' => false], 400);
$action = (string) ($request['action'] ?? '');
if (strlen($raw) > 16384)
    jsonResponse(['ok' => false], 413);
$GLOBALS['bygram_action'] = $action;
$ip = (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
$pdo = db();

function rateLimit(PDO $pdo, string $key, int $limit): void {
    $q = $pdo->prepare("INSERT INTO api_rate_limits (rate_key,window_started,request_count) VALUES (?,UTC_TIMESTAMP(),1)
        ON DUPLICATE KEY UPDATE request_count=IF(window_started<UTC_TIMESTAMP()-INTERVAL 1 MINUTE,1,request_count+1),window_started=IF(window_started<UTC_TIMESTAMP()-INTERVAL 1 MINUTE,UTC_TIMESTAMP(),window_started)");
    $q->execute([$key]);
    $q = $pdo->prepare('SELECT request_count FROM api_rate_limits WHERE rate_key=?'); $q->execute([$key]);
    if ((int) $q->fetchColumn() > $limit) { error_log('BYgram API rate_limit key=' . hash('sha256', $key)); jsonResponse(['ok' => false], 429); }
}
function token(): string { return rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '='); }
function accountLoginUrl(string $mode, string $activation): string {
    $adminUrl = (string) (appConfig()['admin_url'] ?? 'https://byxarek.site/TEST-PROJECT/BYgram/api/admin.php');
    return dirname($adminUrl) . '/account-login.php?' . http_build_query(['mode' => $mode, 'token' => $activation]);
}
function issueTokens(PDO $pdo, int $userId): array {
    $access = token(); $refresh = token();
    $pdo->prepare("INSERT INTO activity_sessions (telegram_id,account_confirmed,access_hash,refresh_hash,access_expires_at,refresh_expires_at) VALUES (?,?,?,?,UTC_TIMESTAMP()+INTERVAL 1 HOUR,UTC_TIMESTAMP()+INTERVAL 30 DAY)")
        ->execute([$userId, 1, hash('sha256', $access), hash('sha256', $refresh)]);
    return ['access_token' => $access, 'refresh_token' => $refresh, 'access_expires' => time() + 3600];
}
function bearer(): string {
    $header = (string) ($_SERVER['HTTP_AUTHORIZATION'] ?? '');
    return str_starts_with($header, 'Bearer ') ? substr($header, 7) : '';
}
function requireAccountSession(int $userId): void {
    if (!validAccountSession(bearer(), $userId)) jsonResponse(['ok' => false, 'needs_registration' => true], 401);
}
function authenticatedSession(PDO $pdo): array {
    $q = $pdo->prepare('SELECT id,telegram_id FROM activity_sessions WHERE access_hash=? AND access_expires_at>UTC_TIMESTAMP() AND revoked_at IS NULL AND device_id IS NULL AND account_confirmed=1');
    $q->execute([hash('sha256', bearer())]);
    $session = $q->fetch();
    if (!$session) jsonResponse(['ok' => false], 401);
    return $session;
}
function viewerIds(mixed $value): array {
    $ids = array_values(array_unique(array_filter(array_map('intval', is_array($value) ? $value : []), static fn(int $id): bool => $id > 0)));
    if (count($ids) > 200) jsonResponse(['ok' => false], 400);
    return $ids;
}
function viewerPermissions(mixed $value): array {
    $result = [];
    foreach (is_array($value) ? $value : [] as $viewer) {
        if (is_array($viewer)) {
            $id = (int) ($viewer['user_id'] ?? 0);
            $permissions = (int) ($viewer['permissions'] ?? 0) & 7;
        } else {
            $id = (int) $viewer;
            $permissions = 4;
        }
        if ($id > 0 && $permissions !== 0) $result[$id] = $permissions;
    }
    if (count($result) > 200) jsonResponse(['ok' => false], 400);
    return $result;
}
function replaceViewers(PDO $pdo, int $ownerId, array $viewers): void {
    $pdo->prepare('DELETE FROM activity_viewers WHERE owner_id=?')->execute([$ownerId]);
    $insert = $pdo->prepare('INSERT INTO activity_viewers (owner_id,viewer_id,permissions) VALUES (?,?,?)');
    foreach ($viewers as $viewerId => $permissions) $insert->execute([$ownerId, $viewerId, $permissions]);
}

rateLimit($pdo, 'ip:' . $ip, 120);
if ($action === 'device_seen') {
    require __DIR__ . '/client-devices.php';
    try {
        reportClientDevice($pdo, $request);
        jsonResponse(['ok' => true]);
    } catch (InvalidArgumentException) {
        jsonResponse(['ok' => false], 400);
    }
}
$pdo->exec('DELETE FROM typing_nonces WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM chat_activity WHERE expires_at<UTC_TIMESTAMP()');
$pdo->exec('DELETE FROM api_rate_limits WHERE window_started<UTC_TIMESTAMP()-INTERVAL 1 DAY');

if ($action === 'activity_start') {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    requireAccountSession($userId);
    rateLimit($pdo, 'activity_start:' . $ip, 30);
    $pdo->prepare('INSERT IGNORE INTO users (telegram_id) VALUES (?)')->execute([$userId]);
    jsonResponse(['ok' => true, 'state' => 'active'] + issueTokens($pdo, $userId));
}
if (in_array($action, ['account_login_start', 'account_login_exchange', 'account_login_webapp'], true)) {
    rateLimit($pdo, $action . ':' . $ip, 30);
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    if ($action === 'account_login_start') {
        $browserToken = token();
        $exchangeToken = token();
        accountLoginAttempt($pdo, $userId, $browserToken, $exchangeToken);
        $result = ['ok' => true, 'auth_url' => accountLoginUrl('account', $browserToken), 'exchange_token' => $exchangeToken];
        $shortName = appConfig()['account_mini_app'] ?? '';
        if (preg_match('/^[A-Za-z0-9_]{3,64}$/D', $shortName)) {
            $result += ['mini_app' => $shortName, 'bot' => appSetting('bot_username'), 'start_param' => $browserToken];
        }
        jsonResponse($result);
    }
    $exchangeToken = $request['exchange_token'] ?? '';
    if (!is_string($exchangeToken) || !preg_match('/^[A-Za-z0-9_-]{43}$/D', $exchangeToken)) jsonResponse(['ok' => false], 400);
    if ($action === 'account_login_webapp') {
        $initData = $request['init_data'] ?? null;
        if (empty(appConfig()['account_mini_app']) || !is_string($initData)) jsonResponse(['ok' => false], 400);
        jsonResponse(completeAccountMiniApp($pdo, $userId, $exchangeToken, $initData));
    }
    jsonResponse(exchangeAccountLogin($pdo, $userId, $exchangeToken));
}
if ($action === 'refresh') {
    $refresh = (string) ($request['refresh_token'] ?? '');
    $q = $pdo->prepare('SELECT id FROM activity_sessions WHERE refresh_hash=? AND refresh_expires_at>UTC_TIMESTAMP() AND revoked_at IS NULL AND device_id IS NULL AND account_confirmed=1');
    $q->execute([hash('sha256', $refresh)]);
    $session = $q->fetch();
    if (!$session) jsonResponse(['ok' => false], 401);
    $access = token();
    $pdo->prepare('UPDATE activity_sessions SET access_hash=?,access_expires_at=UTC_TIMESTAMP()+INTERVAL 1 HOUR WHERE id=?')->execute([hash('sha256', $access), $session['id']]);
    jsonResponse(['ok' => true, 'access_token' => $access, 'access_expires' => time() + 3600]);
}
if ($action === 'badges') {
    rateLimit($pdo, 'badges:' . $ip, 120);
    $ids = viewerIds($request['user_ids'] ?? []);
    $chatIds = viewerIds($request['chat_ids'] ?? []);
    $configured = loadBadges(); $badges = [];
    foreach ($ids as $id) if (badgeFor($configured, 'user', $id)) $badges[] = ['peer_type' => 'user', 'peer_id' => $id];
    foreach ($chatIds as $id) if (badgeFor($configured, 'chat', $id)) $badges[] = ['peer_type' => 'chat', 'peer_id' => $id];
    jsonResponse(['ok' => true, 'badges' => $badges]);
}
if (str_starts_with($action, 'profile_views_')) {
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT);
    if (!$userId || $userId < 1) jsonResponse(['ok' => false], 400);
    rateLimit($pdo, 'profile_views:' . $userId, 120);
    requireAccountSession($userId);
    $pdo->prepare('DELETE FROM profile_views WHERE visited_at<UTC_TIMESTAMP()-INTERVAL 7 DAY')->execute();
    if ($action === 'profile_views_set') {
        if (!is_bool($request['enabled'] ?? null)) jsonResponse(['ok' => false], 400);
        $enabled = $request['enabled'] ? 1 : 0;
        $pdo->beginTransaction();
        $old = $pdo->prepare('SELECT enabled FROM profile_view_settings WHERE telegram_id=? FOR UPDATE');
        $old->execute([$userId]); $wasEnabled = (bool) $old->fetchColumn();
        $pdo->prepare('INSERT INTO profile_view_settings (telegram_id,enabled) VALUES (?,?) ON DUPLICATE KEY UPDATE enabled=VALUES(enabled)')->execute([$userId, $enabled]);
        if (!$enabled || !$wasEnabled) $pdo->prepare('DELETE FROM profile_views WHERE owner_id=? OR viewer_id=?')->execute([$userId, $userId]);
        $pdo->commit();
        jsonResponse(['ok' => true, 'enabled' => (bool) $enabled]);
    }
    $q = $pdo->prepare('SELECT enabled FROM profile_view_settings WHERE telegram_id=?');
    $q->execute([$userId]);
    $enabled = (bool) $q->fetchColumn();
    if ($action === 'profile_views_record') {
        $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
        if (!$ownerId || $ownerId < 1 || $ownerId === $userId) jsonResponse(['ok' => false], 400);
        if (!$enabled) jsonResponse(['ok' => true, 'recorded' => false]);
        $q->execute([$ownerId]);
        if (!(bool) $q->fetchColumn()) jsonResponse(['ok' => true, 'recorded' => false]);
        $name = mb_substr(trim(strip_tags((string) ($request['viewer_name'] ?? ''))), 0, 128);
        $username = (string) ($request['viewer_username'] ?? '');
        if ($name === '' || ($username !== '' && !preg_match('/^[A-Za-z0-9_]{5,64}$/D', $username))) jsonResponse(['ok' => false], 400);
        $identity = $pdo->prepare('SELECT username,first_name,last_name FROM users WHERE telegram_id=?');
        $identity->execute([$userId]); $known = $identity->fetch();
        if ($known) {
            $name = mb_substr(trim((string) $known['first_name'] . ' ' . (string) $known['last_name']), 0, 128) ?: $name;
            $username = (string) ($known['username'] ?? '');
        }
        $pdo->prepare('INSERT INTO profile_views (owner_id,viewer_id,viewer_name,viewer_username,visited_at) VALUES (?,?,?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE viewer_name=VALUES(viewer_name),viewer_username=VALUES(viewer_username),notified_at=IF(visited_at<UTC_TIMESTAMP()-INTERVAL 7 DAY,NULL,notified_at),seen_at=NULL,visited_at=UTC_TIMESTAMP()')->execute([$ownerId, $userId, $name, $username]);
        jsonResponse(['ok' => true, 'recorded' => true]);
    }
    if ($action === 'profile_views_seen') {
        $viewerId = filter_var($request['viewer_id'] ?? null, FILTER_VALIDATE_INT);
        if (!$viewerId || $viewerId < 1) jsonResponse(['ok' => false], 400);
        $pdo->prepare('UPDATE profile_views SET seen_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id=? AND seen_at IS NULL')->execute([$userId, $viewerId]);
        jsonResponse(['ok' => true]);
    }
    if ($action === 'profile_views_list' || $action === 'profile_views_poll' || $action === 'profile_views_summary') {
        if (!$enabled) jsonResponse(['ok' => true, 'enabled' => false, 'views' => []]);
        $extra = $action === 'profile_views_poll' ? ' AND v.notified_at IS NULL' : ($action === 'profile_views_summary' ? ' AND v.seen_at IS NULL' : '');
        $limit = $action === 'profile_views_summary' ? 1 : 100;
        $order = $action === 'profile_views_list' ? '(v.seen_at IS NULL) DESC,' : '';
        $query = $pdo->prepare("SELECT v.viewer_id,v.viewer_name,v.viewer_username,TIMESTAMPDIFF(SECOND,'1970-01-01 00:00:00',v.visited_at) AS visited_at,(v.seen_at IS NULL) AS is_new FROM profile_views v JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY$extra ORDER BY $order v.visited_at DESC LIMIT $limit");
        $query->execute([$userId]); $views = $query->fetchAll();
        if ($action === 'profile_views_list' && $views) {
            $ids = array_column($views, 'viewer_id');
            $marks = implode(',', array_fill(0, count($ids), '?'));
            $pdo->prepare("UPDATE profile_views SET seen_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id IN ($marks) AND seen_at IS NULL")
                ->execute(array_merge([$userId], $ids));
        }
        $expires = time() + 3600;
        foreach ($views as &$view) {
            $viewerId = (int) $view['viewer_id'];
            $signature = hash_hmac('sha256', "$userId:$viewerId:$expires", (string) appConfig()['bot_token']);
            $view['avatar_url'] = "https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?owner=$userId&viewer=$viewerId&expires=$expires&sig=$signature";
        }
        unset($view);
        if ($action === 'profile_views_poll' && $views) {
            $ids = array_column($views, 'viewer_id');
            $marks = implode(',', array_fill(0, count($ids), '?'));
            $pdo->prepare("UPDATE profile_views SET notified_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id IN ($marks)")->execute(array_merge([$userId], $ids));
        }
        if ($action === 'profile_views_summary') {
            $count = $pdo->prepare('SELECT COUNT(*) FROM profile_views v JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.seen_at IS NULL AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY');
            $count->execute([$userId]);
            jsonResponse(['ok' => true, 'enabled' => true, 'new_count' => (int) $count->fetchColumn(), 'views' => $views]);
        }
        jsonResponse(['ok' => true, 'enabled' => true, 'views' => $views]);
    }
    jsonResponse(['ok' => false], 400);
}

if (in_array($action, ['flame_list', 'flame_get', 'flame_sync', 'flame_restore'], true)) {
    require __DIR__ . '/chat-flames.php';
    $idOptions = ['options' => ['min_range' => 1, 'max_range' => 0xffffffffff]];
    $userId = filter_var($request['user_id'] ?? null, FILTER_VALIDATE_INT, $idOptions);
    $peerId = filter_var($request['peer_id'] ?? null, FILTER_VALIDATE_INT, $idOptions);
    if (!$userId || ($action !== 'flame_list' && (!$peerId || $peerId === $userId))) jsonResponse(['ok' => false], 400);
    requireAccountSession($userId);
    rateLimit($pdo, 'flame:' . $userId, 60);
    try {
        jsonResponse($action === 'flame_list' ? chatFlameList($pdo, $userId) : chatFlameRequest($pdo, $userId, $peerId, $action, $request));
    } catch (InvalidArgumentException) {
        jsonResponse(['ok' => false], 400);
    }
}

if (!in_array($action, ['license','update','viewers','clear','get','activity_get','activities'], true))
    jsonResponse(['ok' => false], 404);
$session = authenticatedSession($pdo); $userId = (int) $session['telegram_id'];
rateLimit($pdo, 'session:' . $session['id'], 120);
// Older clients use this response as a session readiness check.
if ($action === 'license') jsonResponse(['ok' => true, 'licensed' => true, 'valid_until' => 0]);

if ($action === 'update') {
    $type = (int) ($request['activity_type'] ?? 4);
    $name = trim(strip_tags((string) ($request['peer_name'] ?? '')));
    $peerId = (int) ($request['peer_id'] ?? 0);
    $peerKind = (int) ($request['peer_kind'] ?? 0);
    if (!in_array($type, [1, 2, 4], true) || !in_array($peerKind, [0, 1, 2, 3, 4], true) || ($type !== 1 && ($peerId === 0 || $name === ''))) jsonResponse(['ok' => false], 400);
    $pdo->beginTransaction();
    $pdo->prepare('INSERT INTO chat_activity (owner_id,activity_type,peer_id,peer_name,peer_kind,expires_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP()+INTERVAL 60 SECOND) ON DUPLICATE KEY UPDATE activity_type=VALUES(activity_type),peer_id=VALUES(peer_id),peer_name=VALUES(peer_name),peer_kind=VALUES(peer_kind),expires_at=VALUES(expires_at)')
        ->execute([$userId, $type, $peerId === 0 ? null : $peerId, mb_substr($name, 0, 100), $peerKind]);
    if (array_key_exists('viewers', $request)) replaceViewers($pdo, $userId, viewerPermissions($request['viewers']));
    $pdo->commit(); jsonResponse(['ok' => true]);
}
if ($action === 'viewers') { $pdo->beginTransaction(); replaceViewers($pdo, $userId, viewerPermissions($request['viewers'] ?? [])); $pdo->commit(); jsonResponse(['ok' => true]); }
if ($action === 'clear') { $pdo->prepare('DELETE FROM chat_activity WHERE owner_id=?')->execute([$userId]); jsonResponse(['ok' => true]); }
if ($action === 'get') {
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    $q = $pdo->prepare('SELECT a.peer_name FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id=? AND a.activity_type=4 AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & 4)<>0');
    $q->execute([$userId, $ownerId ?: 0]); $name = $q->fetchColumn();
    jsonResponse($name === false ? ['ok' => true] : ['ok' => true, 'peer_name' => $name]);
}
if ($action === 'activity_get') {
    $ownerId = filter_var($request['owner_id'] ?? null, FILTER_VALIDATE_INT);
    $q = $pdo->prepare('SELECT a.activity_type,a.peer_id,a.peer_name,a.peer_kind FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id=? AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & a.activity_type)<>0');
    $q->execute([$userId, $ownerId ?: 0]); $activity = $q->fetch();
    jsonResponse($activity === false ? ['ok' => true] : ['ok' => true] + $activity);
}
if ($action === 'activities') {
    $ownerIds = viewerIds($request['owner_ids'] ?? []);
    if ($ownerIds === []) jsonResponse(['ok' => true, 'activities' => []]);
    $marks = implode(',', array_fill(0, count($ownerIds), '?'));
    $q = $pdo->prepare("SELECT a.owner_id,a.activity_type,a.peer_id,a.peer_name,a.peer_kind FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id IN ($marks) AND a.expires_at>UTC_TIMESTAMP() AND (v.permissions & a.activity_type)<>0");
    $q->execute(array_merge([$userId], $ownerIds));
    jsonResponse(['ok' => true, 'activities' => $q->fetchAll()]);
}
error_log('BYgram API invalid_action session=' . $session['id']);
jsonResponse(['ok' => false], 400);
