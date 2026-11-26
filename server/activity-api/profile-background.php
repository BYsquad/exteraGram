<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

$userId = filter_var($_GET['user_id'] ?? null, FILTER_VALIDATE_INT);
if (!$userId || $userId < 1) { http_response_code(400); exit; }
$query = db()->prepare('SELECT image_data,mime,revision FROM profile_backgrounds WHERE telegram_id=?');
$query->execute([$userId]);
$image = $query->fetch();
if (!$image || !hash_equals($image['revision'], (string) ($_GET['v'] ?? ''))) { http_response_code(404); exit; }
header('Content-Type: ' . $image['mime']);
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-cache');
header('ETag: "' . $image['revision'] . '"');
if (($_SERVER['HTTP_IF_NONE_MATCH'] ?? '') === '"' . $image['revision'] . '"') { http_response_code(304); exit; }
header('Content-Length: ' . strlen($image['image_data']));
echo $image['image_data'];
