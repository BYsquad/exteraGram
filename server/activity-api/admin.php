<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

header('Cache-Control: no-store');
header('X-Frame-Options: DENY');
$currentUserId = adminSessionUser();
$page = $_GET['page'] ?? '';
if ($currentUserId < 1 && is_string($page) && preg_match('/^[a-z]+$/D', $page)) {
    $_SESSION['admin_return_page'] = $page;
}
$token = $currentUserId > 0 ? adminCsrfToken() : '';
$botUsername = $currentUserId < 1 ? appSetting('bot_username') : '';
$adminUrl = (string) (appConfig()['admin_url'] ?? 'https://byxarek.site/TEST-PROJECT/BYgram/api/admin.php');
$loginUrl = preg_replace('~/[^/]+$~', '/admin-login.php', $adminUrl);

function adminAttribute(string $value): string {
    return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}
?>
<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Управление BYgram</title>
<link rel="stylesheet" href="../assets/admin-console.css?v=20261125-extera">
</head>
<body>
<section id="admin-root" data-csrf="<?= adminAttribute($token) ?>" data-bot="<?= adminAttribute($botUsername) ?>"
    data-auth-url="<?= adminAttribute($loginUrl) ?>"></section>
<script type="module" src="../assets/admin-console.js?v=20261125-extera"></script>
</body>
</html>
