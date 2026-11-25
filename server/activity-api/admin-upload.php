<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/release-channels.php';

try {
    requireAdminSession();
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') throw new InvalidArgumentException('Method not allowed');
    requireAdminCsrf();
    $channel = releaseChannel($_POST['channel'] ?? 'stable');
    $name = (string) ($_POST['name'] ?? '');
    $offset = filter_var($_POST['offset'] ?? null, FILTER_VALIDATE_INT);
    $total = filter_var($_POST['total'] ?? null, FILTER_VALIDATE_INT);
    $chunk = $_FILES['chunk'] ?? null;
    if (!validReleaseName($name, $channel) || $offset === false || $offset < 0 || $total < 1024 || $total > 536870912
        || !$chunk || $chunk['error'] !== UPLOAD_ERR_OK || $chunk['size'] < 1
        || $chunk['size'] > 5 * 1024 * 1024 || $offset + $chunk['size'] > $total) {
        throw new InvalidArgumentException('Неверный файл APK для выбранного канала');
    }
    $target = dirname(__DIR__) . '/' . $name;
    $uploadId = (string) ($_POST['upload_id'] ?? '');
    if (!preg_match('/^[a-f0-9-]{36}$/D', $uploadId)) throw new InvalidArgumentException('Неверная сессия загрузки APK');
    $part = __DIR__ . '/.upload-' . $uploadId . '-' . $name;
    $input = fopen($chunk['tmp_name'], 'rb');
    if (!$input) throw new RuntimeException('Не удалось прочитать загружаемые данные');
    try {
        if ($offset === 0 && fread($input, 2) !== 'PK') throw new InvalidArgumentException('Выбранный файл не является APK');
        rewind($input);
        $output = fopen($part, 'c+b');
        if (!$output) throw new RuntimeException('Не удалось открыть файл на сервере');
        try {
            if (!flock($output, LOCK_EX)) throw new RuntimeException('Не удалось заблокировать файл');
            if (fstat($output)['size'] !== $offset || fseek($output, $offset) !== 0) {
                throw new RuntimeException('Позиция загрузки не совпала');
            }
            $written = stream_copy_to_stream($input, $output);
            if ($written !== $chunk['size'] || !fflush($output)) {
                ftruncate($output, $offset);
                throw new RuntimeException('Не удалось записать APK');
            }
            $size = $offset + $written;
            if (!empty($_POST['finish'])) {
                $hash = strtolower((string) ($_POST['sha256'] ?? ''));
                $actual = hash_file('sha256', $part);
                if ($size !== $total || !preg_match('/^[a-f0-9]{64}$/D', $hash) || $actual === false || !hash_equals($hash, $actual)) {
                    throw new RuntimeException('APK загружен не полностью');
                }
                if ($channel === 'beta' && $name === (releaseMetadata($channel)['apk_name'] ?? null)) {
                    throw new InvalidArgumentException('Укажите новое имя APK, чтобы сохранить опубликованный файл');
                }
                if (!rename($part, $target)) throw new RuntimeException('Не удалось опубликовать APK');
                chmod($target, 0644);
            }
        } finally {
            fclose($output);
        }
    } finally {
        fclose($input);
    }
    jsonResponse(['ok' => true, 'data' => ['size' => $size]]);
} catch (Throwable $e) {
    jsonResponse(['ok' => false, 'error' => $e->getMessage()], $e instanceof InvalidArgumentException ? 422 : ($e instanceof AdminAuthenticationException ? 401 : 403));
}
