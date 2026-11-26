import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowDownToLine, ArrowRight, Eye, Flame, Folder, History, Settings, Shield, Smartphone } from 'lucide-react';
import '@fontsource-variable/inter';
import '../styles.css';
import logoUrl from '../assets/bygram-logo.png';
import settingsUrl from '../assets/bygram-settings-73059.png';
import ghostUrl from '../assets/bygram-ghost-73059.png';
import spyUrl from '../assets/bygram-spy-73059.png';
import viewsUrl from '../assets/bygram-views-73059.png';
import statisticsUrl from '../assets/bygram-statistics-73059.png';

const pagePath = location.pathname.split('/').pop() || 'index.html';
const activePage = pagePath === 'editions.html' ? 'features.html' : pagePath;
const previewBuild = 73059;
const previewVersion = '13.2.2';
const previewLabel = `BYgram ${previewVersion} · сборка ${previewBuild}`;
const numbers = new Intl.NumberFormat('ru-RU');
const userPlural = new Intl.PluralRules('ru-RU');
const userLabels = { one: 'пользователь', few: 'пользователя', many: 'пользователей', other: 'пользователей' };

function usePublicData(path, initial) {
  const [value, setValue] = useState(initial);
  useEffect(() => {
    const controller = new AbortController();
    fetch('api/' + path, { signal: controller.signal })
      .then(response => response.ok ? response.json() : null)
      .then(data => { if (data) setValue(data); })
      .catch(() => {});
    return () => controller.abort();
  }, [path]);
  return value;
}

function downloadPath(release) {
  return typeof release?.apk_name === 'string' && /^BYgram-beta-[0-9A-Za-z._-]+\.apk$/.test(release.apk_name)
    ? release.apk_name : null;
}

function Header() {
  const links = [
    ['features.html', 'Возможности'],
    ['features.html#screenshots', 'Скриншоты'],
    ['changelog.html', 'Что нового'],
  ];
  return <>
    <a className="skip-link" href="#content">К содержанию</a>
    <header className="site-header">
      <div className="container header-inner">
        <a className="brand" href="index.html"><img src={logoUrl} width="35" height="35" alt="" /> BYgram</a>
        <nav aria-label="Основная навигация">
          {links.map(([href, label]) => <a href={href} key={href} aria-current={activePage === href ? 'page' : undefined}>{label}</a>)}
        </nav>
        <a className="button button--small" href="download.html" aria-current={activePage === 'download.html' ? 'page' : undefined}>
          <ArrowDownToLine aria-hidden="true" /> Скачать
        </a>
      </div>
    </header>
  </>;
}

function Footer() {
  return <footer className="site-footer">
    <div className="container footer-inner">
      <div><a className="brand" href="index.html"><img src={logoUrl} width="35" height="35" alt="" /> BYgram</a>
        <p>Неофициальный клиент Telegram для Android на основе исходного кода <a href="https://github.com/exteraSquad/exteraGram">exteraGram</a>.</p></div>
      <nav aria-label="Ссылки проекта">
        <a href="https://t.me/BYgram_community">Канал</a>
        <a href="https://t.me/BYgram_official">Чат поддержки</a>
        <a href="https://github.com/BYsquad/exteraGram/tree/bygram/exteragram-base">Исходный код BYgram</a>
      </nav>
    </div>
  </footer>;
}

const highlights = [
  { id: 'flame', title: 'Пламя в личных чатах', icon: Flame, text: 'Огонёк отмечает серию дней, когда вы оба писали друг другу.' },
  { id: 'privacy', title: 'Приватность', icon: Shield, text: 'Настройте отметки о прочтении, скройте чаты или защитите их паролем.' },
  { id: 'folders', title: 'Папки и закрепления', icon: Folder, text: 'Создавайте папки и закрепляйте чаты после достижения лимитов Telegram.' },
];

function HomePage() {
  const users = usePublicData('users-count.php', null);
  return <main id="content">
    <section className="container hero">
      <div className="hero-copy">
        <p className="platform"><Smartphone aria-hidden="true" /> Неофициальный клиент Telegram для Android</p>
        <h1>BYgram для Android</h1>
        <p className="lead">Режим призрака, история правок и «Пламя» в личных чатах. BYgram основан на exteraGram.</p>
        <div className="actions">
          <a className="button" href="download.html"><ArrowDownToLine aria-hidden="true" /> Скачать BYgram Debug</a>
          <a className="button button--quiet" href="features.html#screenshots">Скриншоты</a>
        </div>
        {Number.isInteger(users?.count) && <p className="usage"><strong>{numbers.format(users.count)}</strong> <span>{userLabels[userPlural.select(users.count)]} BYgram</span></p>}
      </div>
      <figure className="hero-visual">
        <a className="device device--back" href="features.html#privacy"><img src={ghostUrl} width="1080" height="2400" alt="Режим призрака" /></a>
        <a className="device device--front" href="features.html#settings"><img src={settingsUrl} width="1080" height="2400" alt="Настройки BYgram" fetchPriority="high" /></a>
        <figcaption>{previewLabel}</figcaption>
      </figure>
    </section>
    <section className="section section--light">
      <div className="container">
        <div className="section-head"><h2>Что добавляет BYgram</h2><a className="text-link" href="features.html">Все возможности <ArrowRight aria-hidden="true" /></a></div>
        <div className="highlights">
          {highlights.map(({ id, title, icon: Icon, text }) => <article key={id}>
            <Icon aria-hidden="true" />
            <h3><a href={`features.html#${id}`}>{title}</a></h3>
            <p>{text}</p>
          </article>)}
        </div>
      </div>
    </section>
  </main>;
}

function ReleaseCard({ release }) {
  const href = downloadPath(release);
  return <section className="release-card" id="beta">
    <h2>Тестовая сборка (Debug)</h2>
    <p><strong>Клиент на стадии тестирования.</strong> В работе приложения возможны ошибки и сбои.</p>
    {href ? <>
      <dl className="release-facts">
        <div><dt>Версия</dt><dd>{release.version}{release.version_code ? ' (' + release.version_code + ')' : ''}</dd></div>
        <div><dt>Требования</dt><dd>{release.requirements}</dd></div>
        <div><dt>Размер</dt><dd>{release.size}</dd></div>
      </dl>
      <a className="button" href={href} download><ArrowDownToLine aria-hidden="true" /> Скачать Debug APK</a>
      {Number(release.version_code) < previewBuild && <p>Описание и скриншоты на сайте относятся к новой сборке {previewBuild}. Здесь доступна предыдущая сборка {release.version_code}.</p>}
      {release.sha256 && <details className="checksum"><summary>Контрольная сумма SHA-256</summary><code>{release.sha256}</code></details>}
    </> : <p role="status">Сборка пока недоступна.</p>}
  </section>;
}

function DownloadPage() {
  const beta = usePublicData('beta.json', null);
  return <main id="content" className="container standard">
    <header className="page-heading"><h1>Скачать BYgram</h1></header>
    <ReleaseCard release={beta} />
    <section className="install">
      <h2>Как установить APK</h2>
      <ol><li>Откройте файл из загрузок.</li><li>Если Android попросит, разрешите установку из этого источника.</li><li>Нажмите «Установить» или «Обновить».</li></ol>
      <p>Для обновления поверх BYgram сохраните текущую установку. Удаление приложения сотрёт локальные данные.</p>
    </section>
  </main>;
}

const screenshots = [
  { id: 'settings', image: settingsUrl, label: 'Настройки BYgram' },
  { id: 'privacy', image: ghostUrl, label: 'Режим призрака' },
  { id: 'history', image: spyUrl, label: 'Шпион' },
  { id: 'activity', image: viewsUrl, label: 'Просмотры профиля' },
  { image: statisticsUrl, label: 'Сбор статистики' },
];

const features = [
  { id: 'settings', icon: Settings, title: 'Настройки BYgram', items: [
    ['Где найти', 'В общих настройках приложения откройте «Настройки BYgram». Этот пункт расположен сразу под «Настройками exteraGram».'],
  ] },
  { id: 'privacy', icon: Shield, title: 'Приватность и защита чатов', items: [
    ['Режим призрака', 'В этом клиенте можно отключить отметки о прочтении сообщений и просмотре историй, статус «в сети» и индикатор набора текста. Для сообщений можно отправлять отметку после ответа, реакции или нажатия кнопки бота.'],
    ['Скрытые чаты', 'Уберите выбранные диалоги из списка и обычного поиска. Чтобы открыть скрытые чаты, введите заданный числовой код в поиске.'],
    ['Запароленные чаты', 'Задайте отдельный пароль от четырёх символов для нужного диалога. BYgram скроет содержимое его уведомлений.'],
  ] },
  { id: 'history', icon: History, title: 'Локальная история сообщений', items: [
    ['«Шпион»', 'Сохраняйте удалённые и исчезающие сообщения и предыдущие версии правок, которые успел получить этот клиент.'],
    ['Параметры хранения', 'Отдельно включайте сохранение собственных удалённых сообщений, дат прочтения, последнего замеченного онлайна и уведомления об удалениях. Локальные данные можно очистить в этом же разделе.'],
  ] },
  { id: 'flame', icon: Flame, title: 'Пламя в личных чатах', items: [
    ['Как считается серия', 'Огонёк загорается со счётчиком 1 на второй день подряд, когда оба собеседника отправили сообщение. Новый день начинается в 00:00 по Москве.'],
    ['Где видно огонёк', 'Серия отображается в чате, списке чатов и профиле собеседника. На 1, 5 и каждом кратном десяти дне в чате появляется анимация.'],
    ['Восстановление', 'Последнюю угасшую серию можно вернуть через меню чата. На пару доступно три восстановления за календарный месяц.'],
  ] },
  { id: 'folders', icon: Folder, title: 'Локальные папки и закрепления', items: [
    ['Папки', 'Когда достигнут лимит Telegram, BYgram сохраняет дополнительные папки, их правила и порядок на текущем устройстве.'],
    ['Закрепления', 'Дополнительные закрепления и их порядок сохраняются на текущем устройстве, в том числе в пользовательских папках и архиве.'],
  ] },
  { id: 'activity', icon: Eye, title: 'Активность и просмотры профиля', items: [
    ['Система активности', 'Выбирайте пользователей BYgram, которым видна ваша активность, и настраивайте категории отдельно для каждого. Текущая активность хранится на сервере BYgram до 60 секунд.'],
    ['Просмотры профиля', 'После включения функции видны визиты за последние семь дней. Пользователи с включённой функцией также могут видеть ваши посещения.'],
  ] },
];

function FeaturesPage() {
  useEffect(() => {
    document.getElementById(location.hash.slice(1))?.scrollIntoView();
  }, []);

  return <main id="content" className="container standard">
    <header className="page-heading">
      <h1>Возможности BYgram</h1>
      <p className="lead">Чаты, приватность и история сообщений в {previewLabel}.</p>
    </header>
    <section id="screenshots" className="screenshots">
      <h2>Скриншоты приложения</h2>
      <p className="build-note">Снимки с Android. Нажмите на скриншот, чтобы открыть его в полном размере.</p>
      <ul className="screenshot-grid">
        {screenshots.map(({ id, image, label }) => <li key={image}>
          <figure>
            <a href={image} aria-label={`Открыть скриншот «${label}»`}>
              <img src={image} width="1080" height="2400" alt={label} loading="lazy" />
            </a>
            <figcaption>{id ? <a href={`#${id}`}>{label}</a> : label}</figcaption>
          </figure>
        </li>)}
      </ul>
    </section>
    <div className="feature-list">
      {features.map(({ id, title, icon: Icon, items }) => <section id={id} className="feature" key={id}>
        <Icon className="feature-icon" aria-hidden="true" /><h2>{title}</h2>
        <dl>{items.map(([label, text]) => <React.Fragment key={label}><dt>{label}</dt><dd>{text}</dd></React.Fragment>)}</dl>
      </section>)}
    </div>
  </main>;
}

function ChangelogPage() {
  const beta = usePublicData('beta.json', null);
  return <main id="content" className="container standard">
    <header className="page-heading"><h1>Что нового</h1></header>
    <article className="news">
      <h2>{previewLabel}</h2>
      <ul>
        <li>BYgram перешёл на основу exteraGram 13.2.2 с её настройками оформления и поддержкой файлов .plugin в сборке Full.</li>
        <li>В отдельном меню «Настройки BYgram» собраны режим призрака, скрытые и запароленные чаты, «Шпион», активность и просмотры профиля.</li>
        <li>В списке просмотров профиля появились аватарки. Добавление чатов и людей в настройках открывается кнопкой «+».</li>
        <li>В разделе «Другое» можно отключить сбор статистики.</li>
      </ul>
      <a className="text-link" href="features.html#screenshots">Скриншоты новой сборки <ArrowRight aria-hidden="true" /></a>
      {downloadPath(beta) && <p className="build-note"><a href="download.html#beta">Доступная Debug-сборка BYgram {beta.version} ({beta.version_code})</a></p>}
    </article>
  </main>;
}

function ThanksPage() {
  return <main id="content" className="container standard">
    <header className="page-heading"><h1>Установка BYgram</h1><p className="lead">Откройте загруженный APK и подтвердите установку или обновление.</p></header>
    <a className="button" href="download.html"><ArrowDownToLine aria-hidden="true" /> К загрузкам</a>
  </main>;
}

const Page = {
  'features.html': FeaturesPage,
  'download.html': DownloadPage,
  'changelog.html': ChangelogPage,
  'thanks.html': ThanksPage,
}[activePage] || HomePage;
createRoot(document.getElementById('root')).render(<><Header /><Page /><Footer /></>);
