<?php
declare(strict_types=1);
require __DIR__ . '/bootstrap.php';
require __DIR__ . '/client-devices.php';
require __DIR__ . '/release-channels.php';

header('Cache-Control: no-store');
try{
    $currentUserId=requireAdminSession();
    $pdo=db();$method=$_SERVER['REQUEST_METHOD']??'GET';$input=json_decode((string)file_get_contents('php://input'),true)?:[];
    if ($method === 'GET' && ($_GET['view'] ?? '') === 'flames') {
        require __DIR__ . '/admin-flames.php';
        jsonResponse(['ok' => true, 'data' => adminFlameList($pdo, $_GET)]);
    }
    if ($method === 'GET' && ($_GET['view'] ?? '') === 'users') {
        jsonResponse(['ok' => true, 'data' => adminClientDevices($pdo, $_GET['page'] ?? 1)]);
    }
    if($method==='POST'){
        requireAdminCsrf();
        $action=(string)($input['action']??'');
        if ($action === 'flame.save') {
            require __DIR__ . '/admin-flames.php';
            updateAdminFlame($pdo, $currentUserId, $input);
            jsonResponse(['ok' => true]);
        } elseif($action==='settings.save'){
            $username=ltrim(trim((string)($input['bot_username']??'')),'@');
            if(!preg_match('/^[A-Za-z][A-Za-z0-9_]{3,30}[A-Za-z0-9]$/D',$username))throw new InvalidArgumentException('Username бота: от 5 до 32 символов, латиница, цифры и подчёркивание');
            saveAppSetting('bot_username',$username);
        }elseif($action==='admin.add'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может добавлять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1||isOwner($id))throw new InvalidArgumentException('Укажите Telegram ID нового администратора');
            $pdo->prepare('INSERT IGNORE INTO admins (telegram_id,added_by) VALUES (?,?)')->execute([$id,$currentUserId]);
        }elseif($action==='admin.remove'){
            if(!isOwner($currentUserId))throw new RuntimeException('Только владелец может удалять администраторов');
            $id=(int)($input['telegram_id']??0);if($id<1)throw new InvalidArgumentException('Неверный Telegram ID');
            $pdo->prepare('DELETE FROM admins WHERE telegram_id=?')->execute([$id]);
        }elseif($action==='badge.save'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();$badges[$key]=true;saveBadges($badges);
        }elseif($action==='badge.delete'){
            $type=(string)($input['peer_type']??'user');$id=badgeId($type,$input['telegram_id']??'');
            $key=badgeKey($type,$id);$badges=loadBadges();unset($badges[$key]);if($type==='user')unset($badges[(string)$id]);saveBadges($badges);
        }elseif($action==='release.save'){
            saveReleaseMetadata($input);
        }else throw new InvalidArgumentException('Неизвестное действие');
    }elseif($method!=='GET'){jsonResponse(['ok'=>false,'error'=>'Method not allowed'],405);}
    $data=[];
    $data['is_owner']=isOwner($currentUserId);
    $data['flame_count']=(int)$pdo->query('SELECT COUNT(*) FROM chat_flames f WHERE f.days>0 AND ' . adminFlameVisibility('f'))->fetchColumn();
    $data['settings']=['bot_username'=>appSetting('bot_username')];
    $data['release']=releaseMetadata('stable');
    $data['beta']=releaseMetadata('beta');
    $data['user_count']=clientDeviceCount($pdo);
    $data['badges']=loadBadges();
    $data['admins']=$data['is_owner']?$pdo->query('SELECT a.*,u.username,u.first_name FROM admins a LEFT JOIN users u ON u.telegram_id=a.telegram_id ORDER BY a.created_at DESC')->fetchAll():[];
    jsonResponse(['ok'=>true,'data'=>$data]);
}catch(Throwable $e){if(isset($pdo)&&$pdo->inTransaction())$pdo->rollBack();jsonResponse(['ok'=>false,'error'=>$e->getMessage()],$e instanceof InvalidArgumentException?422:($e instanceof AdminAuthenticationException?401:403));}
