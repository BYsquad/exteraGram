<?php
declare(strict_types=1);

function flameDays(array $state, int $today, int $month): array {
    if ($state['days'] > 0 && $state['last_day'] < $today - 1) {
        $state['lost_day'] = $state['last_day'];
        $state['lost_days'] = $state['days'];
    }
    return [
        'last_day' => $state['last_day'],
        'days' => $state['days'],
        'lost_day' => $state['lost_day'],
        'lost_days' => $state['lost_days'],
        'remaining' => $state['restore_month'] === $month ? 3 - $state['restores'] : 3,
        'revision' => $state['revision'],
        'day' => $today,
    ];
}

function flameApply(array $state, array $ranges): array {
    foreach ($ranges as [$first, $last]) {
        $previous = $state['last_day'];
        if ($last <= $previous) continue;
        if ($first <= $previous + 1) {
            $state['days'] += $last - $previous;
        } else {
            if ($state['days'] > 0) {
                $state['lost_day'] = $previous;
                $state['lost_days'] = $state['days'];
            }
            $state['days'] = $last - $first;
        }
        $state['last_day'] = $last;
    }
    return $state;
}

function flameRestore(array $state, int $today, int $month, int $lostDay): array {
    $status = flameDays($state, $today, $month);
    if ($lostDay <= 0 || $status['lost_day'] !== $lostDay || $status['remaining'] <= 0) return $state;

    $state['days'] = $status['lost_days'] + ($state['last_day'] >= $today - 1 ? $state['days'] : 0);
    $state['last_day'] = max($state['last_day'], $today - 1);
    $state['lost_day'] = $state['lost_days'] = 0;
    $state['restore_month'] = $month;
    $state['restores'] = 4 - $status['remaining'];
    return $state;
}

function flameRanges(mixed $value, int $today): array {
    if (!is_array($value) || !array_is_list($value) || count($value) > 2) throw new InvalidArgumentException();
    $previous = 0;
    foreach ($value as $range) {
        if (!is_array($range) || !array_is_list($range) || count($range) !== 2) throw new InvalidArgumentException();
        [$first, $last] = $range;
        if (!is_int($first) || !is_int($last) || $first <= $previous || $first > $last || $last > $today) {
            throw new InvalidArgumentException();
        }
        $previous = $last;
    }
    return $value;
}

function flameCalendar(): array {
    $now = new DateTimeImmutable('now', new DateTimeZone('Europe/Moscow'));
    $midnight = $now->setTime(0, 0);
    return [
        intdiv($midnight->getTimestamp() + $midnight->getOffset(), 86400),
        (int) $now->format('Ym'),
        $midnight->modify('+1 day')->getTimestamp() - $now->getTimestamp(),
    ];
}

function chatFlameList(PDO $pdo, int $userId): array {
    [$today, , $secondsToMidnight] = flameCalendar();
    $query = $pdo->prepare('SELECT user_low, user_high, days, last_day FROM chat_flames
        WHERE (user_low=? OR user_high=?) AND days>0 AND last_day>=?');
    $query->execute([$userId, $userId, $today - 1]);
    $flames = [];
    while ($row = $query->fetch(PDO::FETCH_ASSOC)) {
        $peerId = (int) $row['user_low'] === $userId ? $row['user_high'] : $row['user_low'];
        $flames[$peerId] = [(int) $row['days'], (int) $row['last_day']];
    }
    return ['ok' => true, 'day' => $today, 'seconds_to_midnight' => $secondsToMidnight, 'flames' => (object) $flames];
}

function chatFlameRequest(PDO $pdo, int $userId, int $peerId, string $action, array $request): array {
    [$today, $month, $secondsToMidnight] = flameCalendar();
    $pair = [min($userId, $peerId), max($userId, $peerId)];
    $empty = array_fill_keys(['last_day', 'days', 'lost_day', 'lost_days', 'restore_month', 'restores', 'revision'], 0);
    $write = $action !== 'flame_get' && ($request['day'] ?? null) === $today;
    $ranges = $action === 'flame_sync' && $write ? flameRanges($request['ranges'] ?? null, $today) : [];
    $write = $write && ($action === 'flame_restore' || $ranges !== []);
    if ($write) {
        $pdo->beginTransaction();
        $pdo->prepare('INSERT IGNORE INTO chat_flames (user_low,user_high) VALUES (?,?)')->execute($pair);
    }
    $select = $pdo->prepare('SELECT last_day,days,lost_day,lost_days,restore_month,restores,revision FROM chat_flames WHERE user_low=? AND user_high=?' . ($write ? ' FOR UPDATE' : ''));
    $select->execute($pair);
    $row = $select->fetch(PDO::FETCH_ASSOC);
    $state = $row ? array_map('intval', $row) : $empty;
    $restored = false;
    if ($write) {
        $next = $state;
        if ($action === 'flame_restore' && is_int($request['lost_day'] ?? null)) {
            $next = flameRestore($state, $today, $month, $request['lost_day']);
            $restored = $next !== $state;
        } elseif ($action === 'flame_sync' && ($request['revision'] ?? null) === $state['revision']) {
            $next = flameApply($state, $ranges);
        }
        if ($next !== $state) {
            $state = $next;
            $state['revision']++;
            $pdo->prepare('UPDATE chat_flames SET last_day=?,days=?,lost_day=?,lost_days=?,restore_month=?,restores=?,revision=? WHERE user_low=? AND user_high=?')
                ->execute([...array_values($state), ...$pair]);
        }
        $pdo->commit();
    }
    return flameDays($state, $today, $month) + [
        'ok' => true,
        'restored' => $restored,
        'seconds_to_midnight' => $secondsToMidnight,
    ];
}
