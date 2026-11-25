<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';

if (($_GET['action'] ?? '') === 'logout') {
    startAdminSession();
    $_SESSION = [];
    session_destroy();
    header('Location: admin.php', true, 303);
    exit;
}

try {
    $id = validateTelegramLogin($_GET);
    startAdminSession();
    session_regenerate_id(true);
    $_SESSION['telegram_id'] = $id;
    unset($_SESSION['admin_csrf']);
    $page = $_SESSION['admin_return_page'] ?? '';
    unset($_SESSION['admin_return_page']);
    header('Location: admin.php' . ($page === '' ? '' : '?page=' . rawurlencode($page)), true, 303);
} catch (Throwable $error) {
    header('Location: admin.php?login=failed', true, 303);
}
