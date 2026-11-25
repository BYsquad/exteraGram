<?php
declare(strict_types=1);

function releaseChannel(mixed $value): string {
    if (!in_array($value, ['stable', 'beta'], true)) throw new InvalidArgumentException('Неизвестный канал релиза');
    return $value;
}

function releaseMetadataPath(string $channel): string {
    return __DIR__ . ($channel === 'beta' ? '/beta.json' : '/release.json');
}

function releaseMetadata(string $channel): ?array {
    $file = releaseMetadataPath($channel);
    $data = is_file($file) ? json_decode((string) file_get_contents($file), true) : null;
    return is_array($data) ? $data : null;
}

function validReleaseName(string $name, string $channel): bool {
    return preg_match('/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/D', $name) === 1
        && str_starts_with(strtolower($name), 'bygram-beta-') === ($channel === 'beta');
}

function saveReleaseMetadata(array $input): void {
    $channel = releaseChannel($input['channel'] ?? 'stable');
    $fields = ['version', 'version_code', 'status', 'requirements', 'package', 'apk_name'];
    $release = [];
    foreach ($fields as $field) {
        $value = $input[$field] ?? '';
        if (!is_string($value) || mb_strlen(trim($value)) > 120) throw new InvalidArgumentException('Проверьте данные релиза');
        $release[$field] = trim($value);
    }
    if (!preg_match('/^[0-9]+(?:\.[0-9]+){1,3}$/D', $release['version'])
        || !preg_match('/^[0-9]{1,12}$/D', $release['version_code'])
        || !validReleaseName($release['apk_name'], $channel)
        || $release['package'] !== 'com.bygram.mobile') {
        throw new InvalidArgumentException('Проверьте версию, код версии и имя файла для выбранного канала');
    }
    if ($channel === 'beta') {
        $number = filter_var($input['bygram_release'] ?? '', FILTER_VALIDATE_INT, [
            'options' => ['min_range' => 1, 'max_range' => 2147483647],
        ]);
        if ($number === false) throw new InvalidArgumentException('Укажите номер релиза BYgram из APK');
        $release['bygram_release'] = $number;
    }
    $apk = dirname(__DIR__) . '/' . $release['apk_name'];
    if (!is_file($apk)) throw new InvalidArgumentException('Сначала загрузите APK для этого релиза');
    $size = filesize($apk);
    $hash = hash_file('sha256', $apk);
    if ($size === false || $hash === false) throw new RuntimeException('Не удалось прочитать опубликованный APK');
    if ($channel === 'beta') {
        $previous = releaseMetadata($channel);
        $previousNumber = (int) ($previous['bygram_release'] ?? 0);
        if ($number < $previousNumber || ($number === $previousNumber && $hash !== ($previous['sha256'] ?? null))) {
            throw new InvalidArgumentException('Для нового APK увеличьте номер релиза BYgram');
        }
    }
    $release['size_bytes'] = $size;
    $release['sha256'] = $hash;
    $release['size'] = number_format($size / 1048576, 1, ',', ' ') . ' МиБ';
    $release['date'] = gmdate('Y-m-d');
    $json = json_encode($release, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT | JSON_THROW_ON_ERROR);
    $target = releaseMetadataPath($channel);
    $temp = tempnam(__DIR__, '.release-');
    if ($temp === false) throw new RuntimeException('Не удалось подготовить данные релиза');
    try {
        if (file_put_contents($temp, $json . PHP_EOL) === false || !chmod($temp, 0644) || !rename($temp, $target)) {
            throw new RuntimeException('Не удалось сохранить данные релиза');
        }
    } finally {
        if (is_file($temp)) unlink($temp);
    }
}
