<?php
declare(strict_types=1);

require __DIR__ . '/../server/activity-api/chat-flames.php';
require __DIR__ . '/../server/activity-api/admin-flames.php';

function check(bool $condition, string $label): void {
    if (!$condition) throw new RuntimeException($label);
}

[$today] = flameCalendar();
check(adminFlameDay(adminFlameDate($today), $today) === $today, 'Admin date uses the streak calendar');
foreach (['2026-02-30', adminFlameDate($today + 1), ['2026-11-17']] as $invalidDate) {
    try {
        adminFlameDay($invalidDate, $today);
        throw new RuntimeException('Invalid admin date accepted');
    } catch (InvalidArgumentException) {
    }
}

$empty = array_fill_keys(['last_day', 'days', 'lost_day', 'lost_days', 'restore_month', 'restores', 'revision'], 0);
$month = 202611;
$firstDay = flameApply($empty, [[99, 99]]);
check($firstDay['days'] === 0 && $firstDay['last_day'] === 99, 'Remember the first mutual day without showing a flame');
check(flameDays($firstDay, 101, $month)['lost_day'] === 0, 'An isolated day is not restorable');
check(flameApply($firstDay, [[100, 100]])['days'] === 1, 'The second consecutive mutual day lights the flame at one');
check(flameApply($firstDay, [[101, 101]])['days'] === 0, 'Nonconsecutive days do not light the flame');
$first = flameApply($empty, [[99, 102]]);
check($first['days'] === 3 && $first['last_day'] === 102, 'Backfill an existing mutual streak from its second day');
check(flameApply($firstDay, [[99, 102]]) === $first, 'History backfill matches incremental sync');
check(flameDays($first, 103, $month)['lost_days'] === 0, 'One day to complete the exchange');
check(flameDays($first, 104, $month)['lost_days'] === 3, 'Expire and offer recovery at the next midnight');
check(flameDays($first, 110, $month)['lost_days'] === 3, 'Last lost streak remains recoverable');
check(flameRestore($first, 110, $month, 102)['last_day'] === 109, 'Recovery after several missed days');
check(flameApply($first, [[101, 102]]) === $first, 'Repeated sync is idempotent');
check(flameApply($first, [[103, 103]])['days'] === 4, 'A contiguous day extends the streak');

$restarted = flameApply($first, [[104, 104]]);
check($restarted['days'] === 0 && $restarted['lost_days'] === 3, 'New pending streak can coexist with recovery');
check(flameDays($restarted, 106, $month)['lost_day'] === 102, 'An expired pending day does not erase the last lost flame');
check(flameApply($restarted, [[106, 106]])['lost_day'] === 102, 'An abandoned pending series does not overwrite the last lost flame');
check(flameApply($restarted, [[105, 105]])['days'] === 1, 'A restarted series also starts at one on the second day');
$restored = flameRestore($restarted, 104, $month, 102);
check($restored['days'] === 3 && $restored['last_day'] === 104, 'Recovery preserves the old count and today’s completed exchange');
check(flameRestore($restored, 104, $month, 102) === $restored, 'Recovery retries do not spend the quota');
$restoredBeforeExchange = flameRestore($first, 104, $month, 102);
check($restoredBeforeExchange['days'] === 3 && $restoredBeforeExchange['last_day'] === 103, 'Recovery before exchange preserves count');
check(flameApply($restoredBeforeExchange, [[104, 104]])['days'] === 4, 'A restored series can continue');
$limit = $restoredBeforeExchange;
$limit['restores'] = 3;
check(flameDays($limit, 104, $month)['remaining'] === 0, 'Shared monthly cap');
check(flameRestore($limit, 105, $month, 103) === $limit, 'Fourth recovery denied');
check(flameRestore($limit, 105, 202612, 103)['restores'] === 1, 'Calendar month resets quota');
$longGap = flameApply($first, [[107, 109]]);
check(flameRestore($longGap, 110, $month, 102)['days'] === 5, 'Recovery preserves a new active streak');
check(flameRanges([[100, 102], [104, 104]], 104) === [[100, 102], [104, 104]], 'Accept chronological spans');
try {
    flameRanges([[101, 105]], 104);
    throw new RuntimeException('Future spans rejected');
} catch (InvalidArgumentException) {
}

if (in_array('sqlite', PDO::getAvailableDrivers(), true)) {
    $pdo = new PDO('sqlite::memory:');
    $pdo->exec('CREATE TABLE chat_flames (user_low INTEGER, user_high INTEGER, days INTEGER, last_day INTEGER)');
    $insert = $pdo->prepare('INSERT INTO chat_flames VALUES (?, ?, ?, ?)');
    foreach ([[1, 2, 3, $today], [2, 7, 1, $today - 1], [2, 8, 0, $today],
              [2, 9, 5, $today - 2], [3, 4, 1, $today]] as $row) {
        $insert->execute($row);
    }
    $list = chatFlameList($pdo, 2);
    check((array) $list['flames'] === [1 => [3, $today], 7 => [1, $today - 1]], 'List contains only visible flames for either side of the pair');
    check(json_encode(chatFlameList($pdo, 6)['flames']) === '{}', 'Empty list preserves the peer map');
}

echo "PASS: flame streak, expiration, recovery, quota, and validation\n";
