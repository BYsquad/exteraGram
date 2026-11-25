<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

if (PHP_SAPI !== 'cli') exit(1);
if (getenv('BYGRAM_TEST_LOCAL') !== '1') throw new RuntimeException('Set BYGRAM_TEST_LOCAL=1; self-test never uses the configured production database.');
$testHost = getenv('BYGRAM_TEST_DB_HOST') ?: 'MySQL';
$testUser = getenv('BYGRAM_TEST_DB_USER') ?: 'root';
$testPassword = getenv('BYGRAM_TEST_DB_PASSWORD') ?: 'root';
$database = 'bygram_self_test_' . getmypid();
$admin = new PDO("mysql:host=$testHost;port=3306;charset=utf8mb4", $testUser, $testPassword, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$admin->exec("CREATE DATABASE `$database` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
register_shutdown_function(static fn() => $admin->exec("DROP DATABASE IF EXISTS `$database`"));
$config['db_dsn'] = "mysql:host=$testHost;port=3306;dbname=$database;charset=utf8mb4";
$config['db_user'] = $testUser;
$config['db_password'] = $testPassword;
$pdo = new PDO($config['db_dsn'], $config['db_user'], $config['db_password'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$pdo->exec((string) file_get_contents(__DIR__ . '/schema.sql'));
require __DIR__ . '/migrations/20261024_access_policy.php';
$widgetValues = ['auth_date' => '1700000000', 'first_name' => 'Owner', 'id' => '1243802669'];
assert(telegramLoginHash($widgetValues, 'self-test-token') === '3d22bf85ec04879c0f627bfffc76a504f489d95a19f0d4fa410cb64340db44c7');
echo "PASS: Telegram Login Widget signature\n";
$pdo->beginTransaction();
try {
$owner = 999999999993; $viewer = 999999999992;
$pdo->prepare('INSERT INTO profile_view_settings (telegram_id,enabled) VALUES (?,1),(?,1)')->execute([$owner, $viewer]);
$pdo->prepare("INSERT INTO profile_views (owner_id,viewer_id,viewer_name,visited_at) VALUES (?,?,?,UTC_TIMESTAMP())")
    ->execute([$owner, $viewer, 'Viewer']);
$visible = $pdo->prepare('SELECT viewer_id FROM profile_views v JOIN profile_view_settings s ON s.telegram_id=v.viewer_id AND s.enabled=1 WHERE v.owner_id=? AND v.visited_at>=UTC_TIMESTAMP()-INTERVAL 7 DAY');
$visible->execute([$owner]);
if ((int) $visible->fetchColumn() !== $viewer) throw new RuntimeException('FAIL: recent profile view hidden');
$unread = $pdo->prepare('SELECT seen_at IS NULL FROM profile_views WHERE owner_id=? AND viewer_id=?');
$unread->execute([$owner, $viewer]);
if (!(bool) $unread->fetchColumn()) throw new RuntimeException('FAIL: new profile view is already read');
$pdo->prepare('UPDATE profile_views SET seen_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id=?')->execute([$owner, $viewer]);
$unread->execute([$owner, $viewer]);
if ((bool) $unread->fetchColumn()) throw new RuntimeException('FAIL: viewed visitor remains new');
$pdo->prepare('UPDATE profile_views SET seen_at=NULL,visited_at=UTC_TIMESTAMP() WHERE owner_id=? AND viewer_id=?')->execute([$owner, $viewer]);
$unread->execute([$owner, $viewer]);
if (!(bool) $unread->fetchColumn()) throw new RuntimeException('FAIL: repeated visit is not new');
$pdo->prepare('UPDATE profile_view_settings SET enabled=0 WHERE telegram_id=?')->execute([$viewer]);
$visible->execute([$owner]);
if ($visible->fetchColumn()) throw new RuntimeException('FAIL: disabled viewer visible');
echo "PASS: profile views respect mutual consent\n";

$required = ['activity_sessions','activity_viewers','admins','api_nonces','api_rate_limits','app_settings','bank_requisites','chat_activity','device_activations','licensed_devices','orders','payment_methods','profile_view_settings','profile_views','promo_codes','referral_adjustments','referral_earnings','referral_settings','referrals','users'];
$tables = $pdo->query('SHOW TABLES')->fetchAll(PDO::FETCH_COLUMN);
if (array_diff($required, $tables)) {
    throw new RuntimeException('FAIL: API schema is incomplete');
}
$methods = $pdo->query('SELECT code FROM payment_methods ORDER BY code')->fetchAll(PDO::FETCH_COLUMN);
if ($methods !== ['stars', 'crypto', 'manual']) {
    throw new RuntimeException('FAIL: payment methods are incomplete');
}
echo "PASS: commerce schema and payment methods\n";
$activityColumns = $pdo->query("SELECT CONCAT(TABLE_NAME,'.',COLUMN_NAME) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='chat_activity' AND COLUMN_NAME='activity_type') OR (TABLE_NAME='activity_viewers' AND COLUMN_NAME='permissions'))")->fetchAll(PDO::FETCH_COLUMN);
sort($activityColumns);
if ($activityColumns !== ['activity_viewers.permissions', 'chat_activity.activity_type']) throw new RuntimeException('FAIL: activity privacy migration');
$activityOwner = 999999999996; $activityViewer = 999999999995;
$pdo->prepare("INSERT INTO chat_activity (owner_id,activity_type,peer_id,peer_name,expires_at) VALUES (?,2,123,'Target',UTC_TIMESTAMP()+INTERVAL 60 SECOND) ON DUPLICATE KEY UPDATE activity_type=2,peer_id=123,peer_name='Target',expires_at=VALUES(expires_at)")->execute([$activityOwner]);
$pdo->prepare('INSERT INTO activity_viewers (owner_id,viewer_id,permissions) VALUES (?,?,4) ON DUPLICATE KEY UPDATE permissions=4')->execute([$activityOwner,$activityViewer]);
$visible=$pdo->prepare('SELECT 1 FROM chat_activity a JOIN activity_viewers v ON v.owner_id=a.owner_id AND v.viewer_id=? WHERE a.owner_id=? AND (v.permissions & a.activity_type)<>0');$visible->execute([$activityViewer,$activityOwner]);
if ($visible->fetchColumn()) throw new RuntimeException('FAIL: category ACL leaked activity');
$pdo->prepare('UPDATE activity_viewers SET permissions=2 WHERE owner_id=? AND viewer_id=?')->execute([$activityOwner,$activityViewer]);$visible->execute([$activityViewer,$activityOwner]);
if (!$visible->fetchColumn()) throw new RuntimeException('FAIL: category ACL denied activity');
echo "PASS: per-viewer activity category ACL\n";
$pdo->prepare('INSERT INTO app_settings (setting_key,setting_value) VALUES (?,?) ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)')->execute(['bot_username', 'BYgram_test_bot']);
$setting=$pdo->prepare('SELECT setting_value FROM app_settings WHERE setting_key=?');$setting->execute(['bot_username']);
if ($setting->fetchColumn() !== 'BYgram_test_bot') throw new RuntimeException('FAIL: bot username setting');
echo "PASS: bot username setting\n";
$adminId = 999999999998;
$pdo->prepare('INSERT INTO admins (telegram_id, added_by) VALUES (?, ?)')->execute([$adminId, 1243802669]);
$query = $pdo->prepare('SELECT added_by FROM admins WHERE telegram_id = ?');
$query->execute([$adminId]);
if ((int) $query->fetchColumn() !== 1243802669) {
    throw new RuntimeException('FAIL: administrator storage');
}
echo "PASS: administrator storage\n";
$referrerId = 999999999997;
$pdo->prepare("INSERT INTO referral_adjustments (telegram_id,amount,currency,note) VALUES (?,7.5,'BYN','test')")->execute([$referrerId]);
$q=$pdo->prepare('SELECT SUM(amount) FROM referral_adjustments WHERE telegram_id=? AND currency=\'BYN\'');$q->execute([$referrerId]);
if((float)$q->fetchColumn()!==7.5)throw new RuntimeException('FAIL: manual referral adjustment');
echo "PASS: manual referral adjustment\n";
$release=json_decode((string)file_get_contents(__DIR__.'/release.json'),true);
if(($release['package']??'')!=='com.bygram.mobile'||!preg_match('/^BYgram-[0-9A-Za-z._-]+\.apk$/',(string)($release['apk_name']??'')))throw new RuntimeException('FAIL: release metadata');
echo "PASS: release metadata\n";
$badges=loadBadges();
foreach($badges as $id=>$badge)if(!preg_match('/^(?:(?:user|chat):[1-9][0-9]*|[1-9][0-9]*)$/D',(string)$id)||($badge!==true&&(!is_array($badge)||!isset($badge['bygram']))))throw new RuntimeException('FAIL: badges.json');
echo "PASS: badge storage\n";
} finally {
    $pdo->rollBack();
}
