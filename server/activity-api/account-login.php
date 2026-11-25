<?php
declare(strict_types=1);

require __DIR__ . '/bootstrap.php';
header('Cache-Control: no-store');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');
$mode = $_GET['mode'] ?? '';
$token = $_GET['token'] ?? '';
$method = $_SERVER['REQUEST_METHOD'] ?? '';
if ($mode !== 'account' || !is_string($token)
    || !preg_match('/^[A-Za-z0-9_-]{43}$/D', $token) || !in_array($method, ['GET', 'POST'], true)) {
    http_response_code(404);
    exit;
}

$pdo = db();
$tokenHash = hash('sha256', $token);
$lookup = 'SELECT telegram_id FROM account_login_attempts
    WHERE login_hash=? AND expires_at>UTC_TIMESTAMP() AND confirmed_at IS NULL';
if ($method === 'POST') {
    try {
        if ((int) ($_SERVER['CONTENT_LENGTH'] ?? 0) > 8192) {
            throw new InvalidArgumentException('Неверные данные входа');
        }
        $identity = json_decode((string) file_get_contents('php://input', false, null, 0, 8193), true);
        $hash = is_array($identity) ? ($identity['hash'] ?? '') : '';
        $id = filter_var($identity['id'] ?? null, FILTER_VALIDATE_INT);
        $authDate = filter_var($identity['auth_date'] ?? null, FILTER_VALIDATE_INT);
        $secret = (string) (appConfig()['bot_token'] ?? '');
        if (!is_string($hash) || !preg_match('/^[a-f0-9]{64}$/D', $hash) || !$id || $id < 1
            || !$authDate || abs(time() - $authDate) > 300 || $secret === ''
            || !hash_equals(telegramLoginHash($identity, $secret), $hash)) {
            throw new InvalidArgumentException('Подпись входа Telegram недействительна. Повторите вход.');
        }
        $pdo->beginTransaction();
        $request = $pdo->prepare($lookup . ' FOR UPDATE');
        $request->execute([$tokenHash]);
        $attempt = $request->fetch();
        if (!$attempt || (int) $attempt['telegram_id'] !== $id) {
            throw new InvalidArgumentException('Войдите под тем же аккаунтом, который открыт в BYgram. Если ссылка устарела, откройте новую из приложения.');
        }
        $seen = $pdo->prepare('INSERT IGNORE INTO typing_nonces (telegram_id,nonce,expires_at) VALUES (?,?,UTC_TIMESTAMP()+INTERVAL 10 MINUTE)');
        $seen->execute([$id, 'auth' . substr($hash, 0, 32)]);
        if ($seen->rowCount() !== 1) throw new InvalidArgumentException('Вход уже использован. Откройте новую ссылку из BYgram.');
        $pdo->prepare('UPDATE account_login_attempts SET confirmed_at=UTC_TIMESTAMP() WHERE login_hash=?')->execute([$tokenHash]);
        $pdo->prepare('INSERT INTO users (telegram_id,username,first_name,last_name,last_seen_at) VALUES (?,?,?,?,UTC_TIMESTAMP())
            ON DUPLICATE KEY UPDATE username=VALUES(username),first_name=VALUES(first_name),last_name=VALUES(last_name),last_seen_at=UTC_TIMESTAMP()')
            ->execute([$id, mb_substr((string) ($identity['username'] ?? ''), 0, 64),
                mb_substr((string) ($identity['first_name'] ?? ''), 0, 128), mb_substr((string) ($identity['last_name'] ?? ''), 0, 128)]);
        $pdo->commit();
        jsonResponse(['ok' => true]);
    } catch (Throwable $error) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        $message = $error instanceof InvalidArgumentException ? $error->getMessage() : 'Не удалось выполнить вход. Попробуйте ещё раз.';
        jsonResponse(['ok' => false, 'error' => $message], 403);
    }
}

$request = $pdo->prepare($lookup);
$request->execute([$tokenHash]);
$active = (bool) $request->fetch();
$username = htmlspecialchars(appSetting('bot_username'), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
?>
<!doctype html>
<html lang="ru">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Вход через Telegram · BYgram</title>
<style>
  :root { color-scheme: light dark; font-family: Inter, system-ui, sans-serif; }
  body { min-height: 100dvh; display: grid; place-items: center; margin: 0; }
  main { width: min(30rem, calc(100% - 3rem)); }
  h1 { font-size: clamp(1.85rem, 6vw, 2.5rem); margin-block: 0.5rem; }
  p { line-height: 1.55; }
</style>
<main>
  <strong>BYgram</strong>
  <h1>Вход через Telegram</h1>
  <?php if ($active): ?>
    <p id="status" role="status">Выберите тот же аккаунт, который открыт в BYgram.</p>
    <section id="widget" aria-label="Вход через Telegram">
      <script>
        window.completeBygramLogin = async user => {
          const status = document.getElementById('status');
          try {
            const response = await fetch(location.href, {
              method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(user)
            });
            const result = await response.json();
            if (!response.ok || !result.ok) throw new Error(result.error || 'Не удалось выполнить вход.');
            status.textContent = 'Вход выполнен. Вернитесь в BYgram.';
            document.getElementById('widget').remove();
          } catch (error) {
            status.textContent = error.message;
          }
        };
      </script>
      <script async src="https://telegram.org/js/telegram-widget.js?22" data-telegram-login="<?= $username ?>"
        data-size="large" data-userpic="false" data-onauth="completeBygramLogin(user)"></script>
    </section>
  <?php else: ?>
    <p>Вернитесь в BYgram. Если вход ещё требуется, откройте новую ссылку из приложения.</p>
  <?php endif; ?>
</main>
</html>
