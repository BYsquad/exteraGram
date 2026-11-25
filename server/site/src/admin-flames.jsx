import React, { useState } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Flame, Pencil, Search } from 'lucide-react';
import { Badge } from '@astryxdesign/core/Badge';
import { Banner } from '@astryxdesign/core/Banner';
import { Button } from '@astryxdesign/core/Button';
import { Card } from '@astryxdesign/core/Card';
import { DateInput } from '@astryxdesign/core/DateInput';
import { Dialog, DialogHeader } from '@astryxdesign/core/Dialog';
import { FormLayout } from '@astryxdesign/core/FormLayout';
import { Icon } from '@astryxdesign/core/Icon';
import { Layout, LayoutContent, LayoutFooter } from '@astryxdesign/core/Layout';
import { Selector } from '@astryxdesign/core/Selector';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Table, pixel, proportional } from '@astryxdesign/core/Table';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TextInput } from '@astryxdesign/core/TextInput';
import { adminRequest, useAdminResource } from './admin-api';

const statuses = {
  active: { label: 'Горит', variant: 'orange' },
  pending: { label: 'Ждёт сообщений', variant: 'neutral' },
  lost: { label: 'Угасло', variant: 'gray' },
};
const statusOptions = [{ value: 'all', label: 'Все статусы' },
  ...Object.entries(statuses).map(([value, { label }]) => ({ value, label }))];
const localDate = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'medium', timeZone: 'UTC' });
const formatDate = date => date ? localDate.format(new Date(date + 'T00:00:00Z')) : '—';

function Participant({ name, username, id }) {
  return <VStack gap={0.5}>
    <Text weight="semibold" maxLines={1}>{name || (username && '@' + username) || 'Telegram ID ' + id}</Text>
    <Text type="supporting" maxLines={1}>{name && username ? '@' + username + ' · ' : ''}{id}</Text>
  </VStack>;
}

function FlameEditor({ row, today, close, saved }) {
  const [days, setDays] = useState(String(row.days));
  const [lastDate, setLastDate] = useState(row.last_date);
  const [lostDays, setLostDays] = useState(String(row.lost_days));
  const [lostDate, setLostDate] = useState(row.lost_date || undefined);
  const [remaining, setRemaining] = useState(String(row.remaining));
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function save(event) {
    event.preventDefault();
    if (busy) return;
    setError('');
    if (!/^\d+$/.test(days) || !lastDate || !/^(?:\d+)?$/.test(lostDays)) {
      setError('Проверьте число дней и дату последнего обмена.');
      return;
    }
    setBusy(true);
    try {
      await adminRequest('admin-api.php', { body: {
        action: 'flame.save', user_low: row.user_low, user_high: row.user_high, revision: row.revision,
        days: Number(days), last_date: lastDate, lost_days: Number(lostDays), lost_date: lostDate || '',
        remaining: Number(remaining),
      } });
      saved();
    } catch (failure) {
      setError(failure.message);
      setBusy(false);
    }
  }

  return <Dialog isOpen onOpenChange={open => { if (!open && !busy) close(); }} purpose="form" width="min(36rem, 100%)">
    <form onSubmit={save}>
      <Layout header={<DialogHeader title="Редактировать «Пламя»" subtitle={`${row.user_low} ↔ ${row.user_high}`} onOpenChange={busy ? undefined : close} />}
        content={<LayoutContent><VStack gap={4}>
          {error && <Banner status="error" title={error} />}
          <FormLayout>
            <TextInput label="Дней в серии" value={days} onChange={setDays} inputMode="numeric" isRequired />
            <DateInput label="Последний засчитанный день" value={lastDate} onChange={setLastDate} max={today} isRequired />
            <Selector label="Доступно восстановлений в этом месяце" value={remaining} onChange={setRemaining} options={['0', '1', '2', '3']} />
          </FormLayout>
          <VStack gap={2}>
            <Heading level={3}>Предыдущая серия</Heading>
            <Text type="supporting">Для очистки укажите 0 дней и очистите дату.</Text>
            <FormLayout>
              <TextInput label="Дней в предыдущей серии" value={lostDays} onChange={setLostDays} inputMode="numeric" />
              <DateInput label="Последний день предыдущей серии" value={lostDate} onChange={setLostDate} max={today} hasClear />
            </FormLayout>
          </VStack>
        </VStack></LayoutContent>}
        footer={<LayoutFooter><HStack gap={2} hAlign="end">
          <Button label="Отмена" onClick={close} isDisabled={busy} />
          <Button label="Сохранить" type="submit" variant="primary" isLoading={busy} />
        </HStack></LayoutFooter>} />
    </form>
  </Dialog>;
}

export function FlamesPage({ revision, notify }) {
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ q: '', status: 'all', page: 1 });
  const [editing, setEditing] = useState(null);
  const [changes, setChanges] = useState(0);
  const query = new URLSearchParams({ view: 'flames', ...filters });
  const { data, error } = useAdminResource('admin-api.php?' + query, revision + changes);

  const columns = [
    { key: 'edit', header: 'Правка', width: pixel(60), align: 'center',
      renderCell: row => <Button label="Изменить" tooltip="Редактировать серию" size="sm" variant="ghost"
        isIconOnly icon={<Icon icon={Pencil} />} onClick={() => setEditing(row)} /> },
    { key: 'user_low', header: 'Участник 1', width: proportional(2, { minWidth: 148 }),
      renderCell: row => <Participant id={row.user_low} name={row.low_name} username={row.low_username} /> },
    { key: 'user_high', header: 'Участник 2', width: proportional(2, { minWidth: 148 }),
      renderCell: row => <Participant id={row.user_high} name={row.high_name} username={row.high_username} /> },
    { key: 'days', header: 'Дни', width: pixel(64),
      renderCell: row => <Text weight="semibold" hasTabularNumbers>{row.days}</Text> },
    { key: 'status', header: 'Статус', width: pixel(156),
      renderCell: row => <Badge {...statuses[row.status]} icon={row.status === 'active' ? <Icon icon={Flame} size="xsm" /> : undefined} /> },
    { key: 'last_date', header: 'Последний обмен', width: pixel(156),
      renderCell: row => formatDate(row.last_date) },
    { key: 'remaining', header: 'Остаток', width: pixel(82),
      renderCell: row => <Text hasTabularNumbers>{row.remaining} / 3</Text> },
  ];

  return <VStack gap={4} minWidth={0}>
    <Card padding={0}>
      <VStack gap={4} padding={4}>
        <HStack gap={4} hAlign="between" wrap="wrap" vAlign="center">
          <Heading level={2}>Список серий</Heading>
          {data && <HStack gap={2} vAlign="center">
            <Icon icon={CalendarDays} size="sm" />
            <Text type="supporting">{formatDate(data.today)} · Москва</Text>
          </HStack>}
        </HStack>
        <form onSubmit={event => {
          event.preventDefault();
          setFilters(previous => ({ ...previous, q: search.trim(), page: 1 }));
        }}>
          <HStack gap={2} wrap="wrap" vAlign="end">
            <TextInput label="Имя, @username или Telegram ID" isLabelHidden placeholder="Имя, @username или Telegram ID"
              value={search} onChange={value => {
                setSearch(value);
                if (!value && filters.q) setFilters(previous => ({ ...previous, q: '', page: 1 }));
              }} width="min(100%, 22rem)" type="search" startIcon={Search} hasClear />
            <Button label="Найти" type="submit" />
            <Selector label="Статус серии" isLabelHidden options={statusOptions} value={filters.status}
              onChange={status => setFilters(previous => ({ ...previous, status, page: 1 }))} />
          </HStack>
        </form>
      </VStack>
      {error && <VStack padding={4}><Banner status="error" title={error}
        endContent={<Button label="Повторить" onClick={() => setChanges(value => value + 1)} />} /></VStack>}
      {!data && !error && <VStack padding={4}><Text type="supporting" role="status">Загрузка серий…</Text></VStack>}
      {data && <>
        {data.rows.length ? <Table data={data.rows} columns={columns}
          idKey={row => row.user_low + '/' + row.user_high} density="balanced" hasHover
          rowCount={data.total} rowIndexStart={(data.page - 1) * data.page_size + 1} /> :
          <VStack gap={2} padding={8} hAlign="center">
            <Icon icon={Flame} size="lg" /><Heading level={3}>Серии не найдены</Heading>
            {(filters.q || filters.status !== 'all') && <Button label="Сбросить фильтры" onClick={() => {
              setSearch(''); setFilters({ q: '', status: 'all', page: 1 });
            }} />}
          </VStack>}
        <HStack padding={4} gap={2} hAlign="between" wrap="wrap" vAlign="center">
          <Text type="supporting">{data.total ? `${(data.page - 1) * data.page_size + 1}–${Math.min(data.page * data.page_size, data.total)} из ${data.total}` : '0 серий'}</Text>
          {data.total > data.page_size && <HStack gap={2}>
            <Button label="Предыдущая страница" isIconOnly icon={<Icon icon={ChevronLeft} />} isDisabled={data.page <= 1}
              onClick={() => setFilters(previous => ({ ...previous, page: data.page - 1 }))} />
            <Button label="Следующая страница" isIconOnly icon={<Icon icon={ChevronRight} />} isDisabled={data.page * data.page_size >= data.total}
              onClick={() => setFilters(previous => ({ ...previous, page: data.page + 1 }))} />
          </HStack>}
        </HStack>
      </>}
    </Card>
    {editing && <FlameEditor row={editing} today={data?.today} close={() => setEditing(null)} saved={() => {
      setEditing(null);
      setChanges(value => value + 1);
      notify('Серия обновлена');
    }} />}
  </VStack>;
}
