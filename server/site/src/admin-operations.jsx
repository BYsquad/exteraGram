import React, { useState } from 'react';
import { ChevronLeft, ChevronRight, ExternalLink, Upload } from 'lucide-react';
import { Badge } from '@astryxdesign/core/Badge';
import { Banner } from '@astryxdesign/core/Banner';
import { Button } from '@astryxdesign/core/Button';
import { Card } from '@astryxdesign/core/Card';
import { CheckboxInput } from '@astryxdesign/core/CheckboxInput';
import { FormLayout } from '@astryxdesign/core/FormLayout';
import { Grid } from '@astryxdesign/core/Grid';
import { Icon } from '@astryxdesign/core/Icon';
import { Selector } from '@astryxdesign/core/Selector';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Table, pixel, proportional } from '@astryxdesign/core/Table';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { adminRequest, useAdminResource } from './admin-api';

const values = form => Object.fromEntries(new FormData(form));

function Field({ name, initial = '', ...props }) {
  const [value, setValue] = useState(String(initial));
  return <TextInput htmlName={name} value={value} onChange={setValue} {...props} />;
}

function FormCard({ title, description, action, run, children, label = 'Сохранить', onSubmit }) {
  const [busy, setBusy] = useState(false);
  return <Card padding={4}>
    <form onSubmit={async event => {
      event.preventDefault();
      if (busy) return;
      const data = values(event.currentTarget);
      setBusy(true);
      try {
        await (onSubmit ? onSubmit(data) : run({ ...data, action }));
      } finally {
        setBusy(false);
      }
    }}>
      <VStack gap={4}>
        <VStack gap={1}>
          <Heading level={2}>{title}</Heading>
          {description && <Text type="supporting">{description}</Text>}
        </VStack>
        <FormLayout>{children}</FormLayout>
        <HStack hAlign="start"><Button label={label} type="submit" variant="primary" isLoading={busy} /></HStack>
      </VStack>
    </form>
  </Card>;
}

function Records({ title, description, rows, columns, rowKey, empty }) {
  return <Card padding={0}>
    <VStack gap={1} padding={4}>
      <Heading level={2}>{title}</Heading>
      {description && <Text type="supporting">{description}</Text>}
    </VStack>
    {rows.length ? <Table data={rows} columns={columns} idKey={rowKey} hasHover />
      : <VStack padding={4}><Text type="supporting">{empty}</Text></VStack>}
  </Card>;
}

export function BadgesPage({ badges, run, confirmAction }) {
  const [type, setType] = useState('user');
  const rows = Object.entries(badges).filter(([, value]) => value === true || value?.bygram).map(([key]) => {
    const [peer_type, telegram_id] = key.includes(':') ? key.split(':') : ['user', key];
    return { key, peer_type, telegram_id };
  });
  const columns = [
    { key: 'telegram_id', header: 'Telegram ID' },
    { key: 'peer_type', header: 'Тип', renderCell: row => row.peer_type === 'chat' ? 'Канал или группа' : 'Пользователь' },
    { key: 'remove', header: 'Действие', renderCell: row => <Button label="Снять" size="sm" variant="ghost"
      onClick={() => confirmAction('Снять значок?', 'Telegram ID ' + row.telegram_id,
        () => run({ action: 'badge.delete', telegram_id: row.telegram_id, peer_type: row.peer_type }))} /> },
  ];
  return <Grid columns={{ minWidth: 300, repeat: 'fit' }} gap={4}>
    <FormCard title="Выдать значок" action="badge.save" run={run} label="Выдать">
      <Selector label="Получатель" htmlName="peer_type" value={type} onChange={setType}
        options={[{ value: 'user', label: 'Пользователь' }, { value: 'chat', label: 'Канал или группа' }]} />
      <Field name="telegram_id" label="Telegram ID" inputMode="numeric" isRequired />
    </FormCard>
    <Records title="Назначенные значки" rows={rows} columns={columns} rowKey="key" empty="Значки пока не выданы." />
  </Grid>;
}

export function AccessPage({ data, run, confirmAction }) {
  const admins = [
    { key: 'telegram_id', header: 'Telegram ID' },
    { key: 'username', header: 'Аккаунт', renderCell: row => row.username ? '@' + row.username : row.first_name || '—' },
    { key: 'remove', header: 'Действие', renderCell: row => <Button label="Удалить" size="sm" variant="ghost"
      onClick={() => confirmAction('Убрать доступ администратора?', 'Telegram ID ' + row.telegram_id,
        () => run({ action: 'admin.remove', telegram_id: row.telegram_id }))} /> },
  ];
  if (!data.is_owner) return <Text type="supporting">Доступом управляет владелец BYgram.</Text>;
  return <Grid columns={{ minWidth: 300, repeat: 'fit' }} gap={4}>
      <FormCard title="Добавить администратора" action="admin.add" run={run} label="Добавить">
        <Field name="telegram_id" label="Telegram ID" inputMode="numeric" isRequired />
      </FormCard>
      <Records title="Администраторы" rows={data.admins} columns={admins} rowKey="telegram_id" empty="Других администраторов пока нет." />
  </Grid>;
}

export function UsersPage({ revision }) {
  const [page, setPage] = useState(1);
  const [attempt, setAttempt] = useState(0);
  const { data, error } = useAdminResource('admin-api.php?view=users&page=' + page, revision + attempt);
  if (error) return <Banner status="error" title={error}
    endContent={<Button label="Повторить" onClick={() => setAttempt(value => value + 1)} />} />;
  if (!data) return <Text role="status">Загрузка списка устройств…</Text>;
  const columns = [
    { key: 'model', header: 'Устройство', width: proportional(2, { minWidth: 155 }),
      renderCell: row => <VStack gap={1}><Text weight="semibold" maxLines={1}>{row.model}</Text><Text type="supporting">{row.device}</Text></VStack> },
    { key: 'app_version', header: 'Версия BYgram', width: pixel(140) },
    { key: 'android_api', header: 'Android API', width: pixel(120) },
    { key: 'first_seen_at', header: 'Первый запуск (UTC)', width: pixel(190) },
    { key: 'last_seen_at', header: 'Последний запуск (UTC)', width: pixel(190) },
  ];
  return <Card padding={0}>
    <VStack padding={4} gap={1}>
      <Heading level={2}>{Number(data.total).toLocaleString('ru-RU')} устройств</Heading>
      <Text type="supporting">Один пользователь в счётчике соответствует одному устройству с запущенным BYgram.</Text>
    </VStack>
    {data.rows.length ? <Table data={data.rows} columns={columns} idKey="device" hasHover
      rowCount={data.total} rowIndexStart={(data.page - 1) * data.page_size + 1} />
      : <VStack padding={4}><Text type="supporting">Устройства пока не зарегистрированы.</Text></VStack>}
    {data.total > data.page_size && <HStack padding={4} hAlign="between" vAlign="center" gap={2}>
      <Text type="supporting">{(data.page - 1) * data.page_size + 1}–{Math.min(data.page * data.page_size, data.total)} из {data.total}</Text>
      <HStack gap={2}>
        <Button label="Предыдущая страница" isIconOnly icon={<Icon icon={ChevronLeft} />} isDisabled={data.page <= 1}
          onClick={() => setPage(data.page - 1)} />
        <Button label="Следующая страница" isIconOnly icon={<Icon icon={ChevronRight} />} isDisabled={data.page * data.page_size >= data.total}
          onClick={() => setPage(data.page + 1)} />
      </HStack>
    </HStack>}
  </Card>;
}

async function uploadApk(file, name, channel, updateProgress) {
  const bytes = new Uint8Array(await crypto.subtle.digest('SHA-256', await file.arrayBuffer()));
  const hash = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
  const chunkSize = 4 * 1024 * 1024;
  const uploadId = crypto.randomUUID();
  for (let offset = 0; offset < file.size; offset += chunkSize) {
    const end = Math.min(file.size, offset + chunkSize);
    const form = new FormData();
    form.append('channel', channel);
    form.append('upload_id', uploadId);
    form.append('name', name);
    form.append('offset', offset);
    form.append('total', file.size);
    form.append('chunk', file.slice(offset, end), 'chunk.apk');
    if (end === file.size) { form.append('finish', '1'); form.append('sha256', hash); }
    await adminRequest('admin-upload.php', { body: form });
    updateProgress(Math.round(end * 100 / file.size));
  }
}

export function PublicationPage({ release, channel = 'stable', run, notify }) {
  const [progress, setProgress] = useState(null);
  const beta = channel === 'beta';
  const current = release || {};
  async function publish(data) {
    const file = data.apk;
    delete data.apk;
    if (!/^[0-9]+(?:\.[0-9]+){1,3}$/.test(data.version) || !/^\d{1,12}$/.test(data.version_code)
      || (beta && (!/^[1-9]\d{0,9}$/.test(data.bygram_release) || Number(data.bygram_release) > 2147483647))
      || !/^BYgram-[0-9A-Za-z._-]{1,60}\.apk$/.test(data.apk_name)
      || data.apk_name.toLowerCase().startsWith('bygram-beta-') !== beta
      || ['status', 'requirements'].some(key => data[key].length > 120)) {
      notify('Проверьте версию, номер релиза BYgram, имя APK и выбранный канал', true);
      return;
    }
    if (file?.name && (file.size < 1024 || file.size > 536870912 || !file.name.toLowerCase().endsWith('.apk'))) {
      notify('Выберите APK размером от 1 КБ до 512 МБ', true);
      return;
    }
    if (beta && file?.name && (Number(data.bygram_release) <= (current.bygram_release ?? 0) || data.apk_name === current.apk_name)) {
      notify('Для нового APK укажите новый номер релиза BYgram и новое имя файла', true);
      return;
    }
    try {
      if (file?.name) {
        setProgress(0);
        await uploadApk(file, data.apk_name, channel, setProgress);
      }
      await run({ ...data, channel, package: 'com.bygram.mobile', action: 'release.save' },
        beta ? 'Бета-версия опубликована' : 'Релиз опубликован');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setProgress(null);
    }
  }
  return <FormCard title={beta ? 'APK бета-версии' : 'APK стабильной версии'}
    description={beta ? 'Имя файла начинается с BYgram-beta-. Сборка появится в отдельном разделе скачивания.' : 'Эти данные появятся в разделе стабильной версии.'}
    onSubmit={publish} label="Опубликовать">
    <HStack gap={2} vAlign="center"><Icon icon={Upload} size="sm" />
      <label>APK (необязательно) <input name="apk" type="file" accept=".apk" /></label>
    </HStack>
    {progress !== null && <Text role="status">Загрузка APK · {progress}%</Text>}
    <Grid columns={{ minWidth: 210, repeat: 'fit' }} gap={3}>
      <Field name="version" label="Версия" initial={current.version} isRequired />
      <Field name="version_code" label="Код версии" initial={current.version_code} inputMode="numeric" isRequired />
      {beta && <Field name="bygram_release" label="Номер релиза BYgram" initial={current.bygram_release?.toString()} inputMode="numeric" isRequired />}
      <Field name="apk_name" label="Имя APK" initial={current.apk_name} placeholder={beta ? 'BYgram-beta-13.2.2.apk' : 'BYgram-13.2.2.apk'} isRequired />
      <Field name="status" label="Подпись релиза" initial={current.status || (beta ? 'Бета-версия' : '')} />
      <Field name="requirements" label="Требования" initial={current.requirements || (beta ? 'Android 7.0+' : '')} />
    </Grid>
  </FormCard>;
}

export function IntegrationsPage({ settings, run }) {
  return <FormCard title="Вход через Telegram" description="Имя интеграции для виджета входа Telegram."
    action="settings.save" run={run}>
    <Field name="bot_username" label="Имя бота" initial={settings.bot_username} isRequired hasSpellCheck={false} />
  </FormCard>;
}
