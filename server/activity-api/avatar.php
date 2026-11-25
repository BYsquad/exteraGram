<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

try {
    $owner = filter_var($_GET['owner'] ?? null, FILTER_VALIDATE_INT);
    $viewer = filter_var($_GET['viewer'] ?? null, FILTER_VALIDATE_INT);
    $expires = filter_var($_GET['expires'] ?? null, FILTER_VALIDATE_INT);
    $signature = (string) ($_GET['sig'] ?? '');
    $token = (string) (appConfig()['bot_token'] ?? '');
    if (!$owner || !$viewer || !$expires || $expires < time() || $expires > time() + 3600
            || $token === '' || !hash_equals(hash_hmac('sha256', "$owner:$viewer:$expires", $token), $signature)) {
        http_response_code(404); exit;
    }
    $q = db()->prepare('SELECT 1 FROM profile_views v JOIN profile_view_settings o ON o.telegram_id=v.owner_id AND o.enabled=1 JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.viewer_id=? AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY');
    $q->execute([$owner, $viewer]);
    if (!$q->fetchColumn()) { http_response_code(404); exit; }
    $context = stream_context_create(['http' => ['timeout' => 6]]);
    $photos = json_decode((string) @file_get_contents('https://api.telegram.org/bot' . $token . '/getUserProfilePhotos?user_id=' . $viewer . '&limit=1', false, $context), true);
    $fileId = $photos['result']['photos'][0][0]['file_id'] ?? null;
    if (!is_string($fileId)) { http_response_code(404); exit; }
    $file = json_decode((string) @file_get_contents('https://api.telegram.org/bot' . $token . '/getFile?file_id=' . rawurlencode($fileId), false, $context), true);
    $path = $file['result']['file_path'] ?? null;
    if (!is_string($path) || !preg_match('~^[A-Za-z0-9_./-]+$~D', $path) || str_contains($path, '..')) { http_response_code(404); exit; }
    $stream = @fopen('https://api.telegram.org/file/bot' . $token . '/' . $path, 'rb', false, $context);
    if (!$stream) { http_response_code(404); exit; }
    $image = stream_get_contents($stream, 524289);
    fclose($stream);
    if ($image === false || strlen($image) > 524288 || strlen($image) < 100) { http_response_code(404); exit; }
    header('Content-Type: image/jpeg');
    header('Cache-Control: private, max-age=3600');
    echo $image;
} catch (Throwable $error) {
    error_log('BYgram avatar failure type=' . get_class($error));
    http_response_code(404);
}
