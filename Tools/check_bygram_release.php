<?php
declare(strict_types=1);

$root = sys_get_temp_dir() . '/bygram-release-' . bin2hex(random_bytes(8));
$api = $root . '/api';
mkdir($root);
mkdir($api);
$first = 'BYgram-beta-first.apk';
$second = 'BYgram-beta-second.apk';
$stable = 'BYgram-stable.apk';

function check(bool $condition, string $message): void {
    if (!$condition) throw new RuntimeException($message);
}

function rejects(array $release): void {
    try {
        saveReleaseMetadata($release);
    } catch (InvalidArgumentException) {
        return;
    }
    throw new RuntimeException('Invalid BYgram release was accepted');
}

try {
    copy(__DIR__ . '/../server/activity-api/release-channels.php', $api . '/release-channels.php');
    require $api . '/release-channels.php';
    foreach ([$first, $second, $stable] as $apk) file_put_contents($root . '/' . $apk, $apk);
    $beta = ['channel' => 'beta', 'version' => '13.2.2', 'version_code' => '73049',
        'package' => 'com.bygram.mobile', 'apk_name' => $first];

    rejects($beta);
    rejects($beta + ['bygram_release' => '2147483648']);
    saveReleaseMetadata($beta + ['bygram_release' => '1']);
    saveReleaseMetadata($beta + ['bygram_release' => '1', 'status' => 'Debug']);
    $beta['apk_name'] = $second;
    rejects($beta + ['bygram_release' => '1']);
    check(releaseMetadata('beta')['apk_name'] === $first, 'A rejected update changed public metadata');
    saveReleaseMetadata($beta + ['bygram_release' => '2']);
    rejects($beta + ['bygram_release' => '1']);
    $published = releaseMetadata('beta');
    check($published['bygram_release'] === 2 && $published['version_code'] === '73049',
        'BYgram release number must be independent of Android version code');

    saveReleaseMetadata(array_replace($beta, ['channel' => 'stable', 'apk_name' => $stable]));
    check(!isset(releaseMetadata('stable')['bygram_release']), 'Stable metadata changed');
    echo "BYgram release checks passed\n";
} finally {
    foreach ([$root . '/' . $first, $root . '/' . $second, $root . '/' . $stable,
        $api . '/beta.json', $api . '/release.json', $api . '/release-channels.php'] as $path) {
        if (is_file($path)) unlink($path);
    }
    rmdir($api);
    rmdir($root);
}
