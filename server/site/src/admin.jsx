import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowUpRight, Flame, FlaskConical, KeyRound, LayoutDashboard, LogOut, RefreshCw, ShieldCheck, Smartphone, Tags, Upload } from 'lucide-react';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/core/astryx.css';
import { AppShell } from '@astryxdesign/core/AppShell';
import { Banner } from '@astryxdesign/core/Banner';
import { Button } from '@astryxdesign/core/Button';
import { Card } from '@astryxdesign/core/Card';
import { Center } from '@astryxdesign/core/Center';
import { Dialog, DialogHeader } from '@astryxdesign/core/Dialog';
import { Divider } from '@astryxdesign/core/Divider';
import { Grid } from '@astryxdesign/core/Grid';
import { Icon } from '@astryxdesign/core/Icon';
import { InternationalizationProvider } from '@astryxdesign/core/i18n';
import { Layout, LayoutContent, LayoutFooter } from '@astryxdesign/core/Layout';
import ruRU from '@astryxdesign/core/locales/ru-RU.generated.js';
import { NavIcon } from '@astryxdesign/core/NavIcon';
import { SideNav, SideNavItem, SideNavSection } from '@astryxdesign/core/SideNav';
import { HStack, VStack } from '@astryxdesign/core/Stack';
import { Heading, Text } from '@astryxdesign/core/Text';
import { TopNav, TopNavHeading } from '@astryxdesign/core/TopNav';
import { Theme } from '@astryxdesign/core/theme';
import { defaultThemeSlug, themes } from './astryx-themes';
import { adminRequest, useAdminResource } from './admin-api';
import { FlamesPage } from './admin-flames';
import { AccessPage, BadgesPage, IntegrationsPage, PublicationPage, UsersPage } from './admin-operations';

const root = document.getElementById('admin-root');
const query = new URLSearchParams(location.search);
const pages = {
  home: { label: 'Обзор', icon: LayoutDashboard },
  flames: { label: 'Пламя', icon: Flame, summary: 'Серии личных чатов, даты обмена и остаток восстановлений.' },
  badges: { label: 'Значки', icon: Tags },
  publication: { label: 'Публикация APK', icon: Upload },
  beta: { label: 'Бета-версия', icon: FlaskConical },
  users: { label: 'Пользователи', icon: Smartphone },
  access: { label: 'Администраторы', icon: ShieldCheck },
  integrations: { label: 'Интеграции', icon: KeyRound },
};
const active = Object.hasOwn(pages, query.get('page')) ? query.get('page') : 'home';
const pageHref = page => page === 'home' ? 'admin.php' : 'admin.php?page=' + page;

function Login() {
  const widget = useRef(null);
  const [unavailable, setUnavailable] = useState(false);
  useEffect(() => {
    const host = widget.current;
    const script = document.createElement('script');
    script.src = 'https://telegram.org/js/telegram-widget.js?22';
    script.async = true;
    script.dataset.telegramLogin = root.dataset.bot;
    script.dataset.size = 'large';
    script.dataset.userpic = 'false';
    script.dataset.authUrl = root.dataset.authUrl;
    script.onerror = () => setUnavailable(true);
    host.appendChild(script);
    return () => host.replaceChildren();
  }, []);
  return <Center axis="both" padding={6} style={{ minHeight: '100dvh', backgroundColor: 'var(--color-background-body)' }}>
    <Card padding={8} width="100%" maxWidth="28rem" elevation="low">
      <VStack gap={4} hAlign="stretch">
        <VStack gap={1}>
          <Text weight="bold" color="accent">BYgram</Text>
          <Heading level={1}>Вход в панель</Heading>
          <Text type="supporting">Войдите через Telegram с аккаунта администратора.</Text>
        </VStack>
        {query.get('login') === 'failed' && <Banner status="error" title="Не удалось войти через Telegram"
          description="Проверьте, что у вашего аккаунта есть доступ к панели." />}
        {unavailable && <Banner status="error" title="Telegram не загрузился"
          endContent={<Button label="Повторить" onClick={() => location.reload()} />} />}
        <VStack ref={widget} hAlign="start" />
      </VStack>
    </Card>
  </Center>;
}

function HomePage({ data }) {
  const metrics = [
    { title: 'Серии «Пламени»', value: data.flame_count, page: 'flames', variant: 'orange', icon: Flame },
    { title: 'Пользователи BYgram', value: data.user_count, page: 'users', variant: 'default', icon: Smartphone },
  ];
  return <VStack gap={6}>
    <Grid columns={{ minWidth: 220, repeat: 'fit' }} gap={4}>
      {metrics.map(({ title, value, page, variant, icon }) => <Card key={page} variant={variant} padding={4}>
        <VStack gap={3}>
          <HStack gap={2} vAlign="center"><Icon icon={icon} size="sm" /><Text weight="semibold">{title}</Text></HStack>
          <Heading level={2} type="display-2">{Number(value).toLocaleString('ru-RU')}</Heading>
          <HStack hAlign="start"><Button label="Открыть" aria-label={'Открыть: ' + title} href={pageHref(page)}
            variant="ghost" endContent={<Icon icon={ArrowUpRight} />} /></HStack>
        </VStack>
      </Card>)}
    </Grid>
    <Card padding={4}>
      <HStack gap={4} hAlign="between" wrap="wrap" vAlign="center">
        <VStack gap={1}>
          <Text type="supporting">Публичный релиз</Text>
          <Heading level={2}>{data.release ? 'BYgram ' + data.release.version : 'Релиз ещё не опубликован'}</Heading>
          {data.release && <Text type="supporting">{data.release.requirements} · {data.release.size}</Text>}
        </VStack>
        <Button label="Управлять публикацией" href={pageHref('publication')} icon={<Icon icon={Upload} />} />
      </HStack>
    </Card>
  </VStack>;
}

function ConfirmAction({ action, close }) {
  const [busy, setBusy] = useState(false);
  return <Dialog isOpen onOpenChange={open => { if (!open && !busy) close(); }} purpose="form">
    <Layout header={<DialogHeader title={action.title} onOpenChange={busy ? undefined : close} />}
      content={<LayoutContent><Text>{action.description}</Text></LayoutContent>}
      footer={<LayoutFooter><HStack gap={2} hAlign="end">
        <Button label="Отмена" onClick={close} isDisabled={busy} />
        <Button label="Подтвердить" variant="destructive" isLoading={busy} onClick={async () => {
          setBusy(true);
          await action.execute();
          close();
        }} />
      </HStack></LayoutFooter>} />
  </Dialog>;
}

function AdminApp() {
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState(null);
  const [confirmation, setConfirmation] = useState(null);
  const refresh = () => setRevision(value => value + 1);
  const notify = (title, bad = false) => setNotice({ title, status: bad ? 'error' : 'success' });
  const confirmAction = (title, description, execute) => setConfirmation({ title, description, execute });
  const { data, error } = useAdminResource('admin-api.php', revision);

  async function run(body, message = 'Изменения сохранены') {
    try {
      await adminRequest('admin-api.php', { body });
      refresh();
      notify(message);
    } catch (failure) {
      notify(failure.message, true);
    }
  }

  const navItem = page => <SideNavItem key={page} label={pages[page].label} icon={pages[page].icon}
    href={pageHref(page)} isSelected={active === page} />;
  return <AppShell variant="elevated" contentPadding={0}
    topNav={<TopNav label="Панель управления"
      heading={<TopNavHeading heading="BYgram" subheading="Панель управления" headingHref={pageHref('home')}
        logo={<NavIcon icon={<Icon icon={ShieldCheck} size="sm" />} />} />}
      endContent={<Button label="Обновить" tooltip="Обновить данные" isIconOnly icon={<Icon icon={RefreshCw} />}
        variant="ghost" onClick={refresh} />} />}
    sideNav={<SideNav label="Разделы панели"
      footer={<SideNavSection title="Сессия" isHeaderHidden>
        <SideNavItem label="Выйти" href="admin-login.php?action=logout" icon={LogOut} />
      </SideNavSection>}>
      <SideNavSection title="BYgram">{['home', 'flames', 'badges'].map(navItem)}</SideNavSection>
      <Divider />
      <SideNavSection title="Администрирование">{['publication', 'beta', 'users', 'access', 'integrations'].map(navItem)}</SideNavSection>
    </SideNav>}>
    <Layout height="auto" contentWidth="88rem" content={<LayoutContent padding={6}>
      <VStack gap={6} minWidth={0}>
        <VStack gap={1}>
          <Heading level={1}>{pages[active].label}</Heading>
          {pages[active].summary && <Text type="supporting">{pages[active].summary}</Text>}
        </VStack>
        {notice && <Banner {...notice} isDismissable dismissLabel="Закрыть уведомление" onDismiss={() => setNotice(null)} />}
        {error && <Banner status="error" title={error} endContent={<Button label="Повторить" onClick={refresh} />} />}
        {!data && !error && !['flames', 'users'].includes(active) && <Text role="status">Загрузка панели…</Text>}
        {active === 'flames' && <FlamesPage revision={revision} notify={notify} />}
        {active === 'users' && <UsersPage revision={revision} />}
        {data && active === 'home' && <HomePage data={data} />}
        {data && active === 'badges' && <BadgesPage badges={data.badges} run={run} confirmAction={confirmAction} />}
        {data && active === 'access' && <AccessPage data={data} run={run} confirmAction={confirmAction} />}
        {data && active === 'publication' && <PublicationPage release={data.release} run={run} notify={notify} />}
        {data && active === 'beta' && <PublicationPage release={data.beta} channel="beta" run={run} notify={notify} />}
        {data && active === 'integrations' && <IntegrationsPage settings={data.settings} run={run} />}
      </VStack>
    </LayoutContent>} />
    {confirmation && <ConfirmAction action={confirmation} close={() => setConfirmation(null)} />}
  </AppShell>;
}

createRoot(root).render(
  <InternationalizationProvider locale="ru-RU" messages={{ 'ru-RU': ruRU }}>
    <Theme theme={themes[defaultThemeSlug]}>
      {root.dataset.csrf ? <AdminApp /> : <Login />}
    </Theme>
  </InternationalizationProvider>,
);
