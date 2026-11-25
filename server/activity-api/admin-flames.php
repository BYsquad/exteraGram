<?php
declare(strict_types=1);

require_once __DIR__ . '/chat-flames.php';

function adminFlameList(PDO $pdo, array $filters): array {
    [$today, $month] = flameCalendar();
    $pageSize = 30;
    $page = max(1, filter_var($filters['page'] ?? null, FILTER_VALIDATE_INT) ?: 1);
    $search = trim(is_string($filters['q'] ?? null) ? $filters['q'] : '');
    if (mb_strlen($search) > 80) throw new InvalidArgumentException('Поиск ограничен 80 символами');
    $from = ' FROM chat_flames f LEFT JOIN users a ON a.telegram_id=f.user_low LEFT JOIN users b ON b.telegram_id=f.user_high';
    $where = ' WHERE f.days > 0';
    $args = [];
    $status = $filters['status'] ?? 'all';
    switch ($status) {
        case 'all': break;
        case 'active': $where .= ' AND f.last_day = ?'; $args[] = $today; break;
        case 'pending': $where .= ' AND f.last_day = ?'; $args[] = $today - 1; break;
        case 'lost': $where .= ' AND f.last_day < ?'; $args[] = $today - 1; break;
        default: throw new InvalidArgumentException('Неизвестный статус серии');
    }
    if ($search !== '') {
        if (ctype_digit($search)) {
            $where .= ' AND (f.user_low = ? OR f.user_high = ?)';
            array_push($args, $search, $search);
        } else {
            $needle = '%' . strtr(ltrim($search, '@'), ['!' => '!!', '%' => '!%', '_' => '!_']) . '%';
            $where .= " AND (a.username LIKE ? ESCAPE '!' OR b.username LIKE ? ESCAPE '!'
                OR CONCAT_WS(' ', a.first_name, a.last_name) LIKE ? ESCAPE '!'
                OR CONCAT_WS(' ', b.first_name, b.last_name) LIKE ? ESCAPE '!')";
            array_push($args, $needle, $needle, $needle, $needle);
        }
    }
    $count = $pdo->prepare('SELECT COUNT(*)' . $from . $where);
    $count->execute($args);
    $total = (int) $count->fetchColumn();
    $page = min($page, max(1, (int) ceil($total / $pageSize)));
    $offset = ($page - 1) * $pageSize;
    $query = $pdo->prepare("SELECT f.*, a.username AS low_username, b.username AS high_username,
        TRIM(CONCAT_WS(' ', a.first_name, a.last_name)) AS low_name,
        TRIM(CONCAT_WS(' ', b.first_name, b.last_name)) AS high_name"
        . $from . $where . " ORDER BY f.last_day DESC, f.user_low, f.user_high LIMIT $pageSize OFFSET $offset");
    $query->execute($args);
    $rows = [];
    foreach ($query->fetchAll() as $row) {
        $lastDay = (int) $row['last_day'];
        $rows[] = [
            'user_low' => (string) $row['user_low'], 'user_high' => (string) $row['user_high'],
            'low_username' => $row['low_username'], 'high_username' => $row['high_username'],
            'low_name' => $row['low_name'], 'high_name' => $row['high_name'],
            'days' => (int) $row['days'], 'last_date' => adminFlameDate($lastDay),
            'lost_days' => (int) $row['lost_days'], 'lost_date' => adminFlameDate((int) $row['lost_day']),
            'remaining' => (int) $row['restore_month'] === $month ? 3 - (int) $row['restores'] : 3,
            'status' => $lastDay === $today ? 'active' : ($lastDay === $today - 1 ? 'pending' : 'lost'),
            'revision' => (int) $row['revision'],
        ];
    }
    return ['rows' => $rows, 'page' => $page, 'page_size' => $pageSize, 'total' => $total, 'today' => adminFlameDate($today)];
}

function adminFlameDate(int $day): string {
    return $day > 0 ? gmdate('Y-m-d', $day * 86400) : '';
}

function adminFlameDay(mixed $value, int $today): int {
    if (is_string($value) && preg_match('/^\d{4}-\d{2}-\d{2}$/D', $value)) {
        $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value, new DateTimeZone('UTC'));
        if ($date && $date->format('Y-m-d') === $value) {
            $day = intdiv($date->getTimestamp(), 86400);
            if ($day > 0 && $day <= $today) return $day;
        }
    }
    throw new InvalidArgumentException('Укажите существующую дату не позднее сегодняшней по Москве');
}

function updateAdminFlame(PDO $pdo, int $admin, array $input): void {
    [$today, $month] = flameCalendar();
    $idRange = ['options' => ['min_range' => 1, 'max_range' => 0xffffffffff]];
    $low = filter_var($input['user_low'] ?? null, FILTER_VALIDATE_INT, $idRange);
    $high = filter_var($input['user_high'] ?? null, FILTER_VALIDATE_INT, $idRange);
    $revision = $input['revision'] ?? null;
    $days = $input['days'] ?? null;
    $lostDays = $input['lost_days'] ?? null;
    $remaining = $input['remaining'] ?? null;
    if (!$low || !$high || $low >= $high || !is_int($revision) || $revision < 0
        || !is_int($days) || $days < 1 || $days > $today || !is_int($lostDays) || $lostDays < 0
        || !is_int($remaining) || $remaining < 0 || $remaining > 3) {
        throw new InvalidArgumentException('Проверьте участников, число дней и остаток восстановлений');
    }
    $lastDay = adminFlameDay($input['last_date'] ?? null, $today);
    $lostDate = $input['lost_date'] ?? '';
    $lostDay = $lostDate === '' ? 0 : adminFlameDay($lostDate, $today);
    if ($days > $lastDay || $lostDays > $lostDay || ($lostDays === 0) !== ($lostDay === 0)
        || ($lostDay > 0 && $lostDay >= $lastDay - 1)) {
        throw new InvalidArgumentException('Укажите дни и дату предыдущей серии до разрыва или очистите оба поля');
    }
    $update = $pdo->prepare('UPDATE chat_flames SET last_day=?,days=?,lost_day=?,lost_days=?,restore_month=?,restores=?,revision=revision+1
        WHERE user_low=? AND user_high=? AND revision=? AND days>0');
    $update->execute([$lastDay, $days, $lostDay, $lostDays, $month, 3 - $remaining, $low, $high, $revision]);
    if ($update->rowCount() !== 1) {
        jsonResponse(['ok' => false, 'error' => 'Серия уже изменилась. Обновите список и откройте её заново.'], 409);
    }
    error_log("BYgram flame edit admin=$admin pair=$low/$high revision=" . ($revision + 1));
}
