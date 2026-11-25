import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { ArrowDownToLine, ArrowRight, Blocks, Flame, Palette, Shield, Smartphone } from 'lucide-react';
import '@fontsource-variable/inter';
import '../styles.css';
import logoUrl from '../assets/bygram-logo.png';
import ghostUrl from '../assets/bygram-ghost.png';
import spyUrl from '../assets/bygram-spy.png';

const pagePath = location.pathname.split('/').pop() || 'index.html';
const activePage = pagePath === 'editions.html' ? 'features.html' : pagePath;
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
        <p>Неофициальный клиент Telegram для Android.</p></div>
      <nav aria-label="Ссылки проекта">
        <a href="https://t.me/BYgram_community">Канал</a>
        <a href="https://t.me/BYgram_official">Чат поддержки</a>
      </nav>
    </div>
  </footer>;
}

const highlights = [
  { id: 'flame', title: 'Пламя в личных чатах', icon: Flame, text: 'В бете огонёк показывает, сколько дней подряд вы оба писали друг другу.' },
  { id: 'privacy', title: 'Приватность', icon: Shield, text: 'Настройте отметки о прочтении, скройте чаты или защитите их паролем.' },
];

function HomePage() {
  const beta = usePublicData('beta.json', null);
  const users = usePublicData('users-count.php', null);
  return <main id="content">
    <section className="container hero">
      <div className="hero-copy">
        <p className="platform"><Smartphone aria-hidden="true" /> Для Android · Debug{beta?.version && ` ${beta.version}`}</p>
        <h1>Telegram<br />по вашим правилам.</h1>
        <p className="lead">«Пламя» в личных чатах, локальные папки, настройки приватности и плагины exteraGram.</p>
        <div className="actions">
          <a className="button" href="download.html"><ArrowDownToLine aria-hidden="true" /> Скачать BYgram Debug</a>
        </div>
        {Number.isInteger(users?.count) && <p className="usage"><strong>{numbers.format(users.count)}</strong> <span>{userLabels[userPlural.select(users.count)]} BYgram</span></p>}
      </div>
      <div className="hero-visual">
        <div className="device device--front"><img src={ghostUrl} width="1080" height="2400" alt="Настройки режима призрака" fetchPriority="high" /></div>
        <div className="flame-preview" aria-hidden="true"><Flame /><strong>12</strong><span>дней подряд</span></div>
      </div>
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
    <section className="container closing">
      <div><h2>Меняйте то, чем пользуетесь каждый день.</h2><p>От видеообоев и оформления профиля до сохранения истории правок.</p></div>
      <a className="button button--quiet" href="features.html">Посмотреть функции <ArrowRight aria-hidden="true" /></a>
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

const features = [
  { id: 'flame', icon: Flame, title: 'Пламя в переписке', items: [
    ['Серия личного чата', 'День засчитывается, когда оба собеседника отправляют сообщение. В 00:00 по Москве огонёк сереет, пока оба не напишут снова.'],
    ['Анимация', 'На 1, 5, 10 и каждом следующем десятке чат заполняют пламя и искры.'],
    ['Восстановление', 'Последнюю потерянную серию можно вернуть через меню чата. На пару доступно три восстановления за календарный месяц.'],
  ] },
  { id: 'privacy', icon: Shield, title: 'Приватность и защита', image: ghostUrl, alt: 'Настройки режима призрака', items: [
    ['Режим призрака', 'Настройте отправку прочтения, видимость онлайна и печати. Прочтение можно отправлять после ответа, реакции или явного действия.'],
    ['Скрытые чаты', 'Уберите выбранные диалоги из списка и поиска. Числовой код открывает отдельную вкладку.'],
    ['Пароль для чата', 'Установите пароль на отдельный диалог. Уведомления защищённых чатов скрывают содержимое.'],
  ] },
  { id: 'history', icon: Blocks, title: 'История сообщений', image: spyUrl, alt: 'Параметры хранения истории', items: [
    ['«Шпион»', 'Храните на устройстве удалённые сообщения и прежние версии отредактированных сообщений, которые BYgram успел получить.'],
    ['Контроль хранения', 'Выбирайте, какие события сохранять, и очищайте локальную историю в настройках.'],
  ] },
  { id: 'folders', icon: Blocks, title: 'Локальные папки и закрепления', items: [
    ['Папки', 'Папки сверх лимита Telegram хранятся на этом устройстве.'],
    ['Закрепления', 'Дополнительные закрепления сохраняются локально.'],
  ] },
  { id: 'appearance', icon: Palette, title: 'Оформление exteraGram', items: [
    ['Внешний вид', 'Темы Material You, паки иконок и альтернативные значки приложения.'],
  ] },
  { id: 'plugins', icon: Blocks, title: 'Плагины exteraGram', items: [
    ['Установка', 'Используйте плагины формата .plugin в полной версии BYgram.'],
  ], link: 'https://github.com/exteraSquad/exteraGram/tree/main/plugins-pysdk' },
];
function FeaturesPage() {
  return <main id="content" className="container standard">
    <header className="page-heading"><h1>Возможности BYgram</h1><p className="lead">Дополнительные настройки чатов, приватности и оформления.</p></header>
    <div className="feature-list">
      {features.map(({ id, title, icon: Icon, image, alt, caption, items, link }) => <section id={id} className={image ? 'feature feature--split' : 'feature'} key={id}>
        <div>
          <Icon className="feature-icon" aria-hidden="true" /><h2>{title}</h2>
          <dl>{items.map(([label, text]) => <div key={label}><dt>{label}</dt><dd>{text}</dd></div>)}</dl>
          {link && <a className="text-link" href={link}>Документация SDK <ArrowRight aria-hidden="true" /></a>}
        </div>
        {image && <figure>
          <img src={image} width={1080} height={caption ? 1220 : 2400} alt={alt} loading="lazy" />
          {caption && <figcaption>{caption}</figcaption>}
        </figure>}
      </section>)}
    </div>
  </main>;
}

function ChangelogPage() {
  const beta = usePublicData('beta.json', null);
  return <main id="content" className="container standard">
    <header className="page-heading"><h1>Что нового</h1></header>
    <article className="news">
      <h2>BYgram {beta?.version || '13.2.2'} · бета</h2>
      <ul>
        <li>Новая основа exteraGram со своим оформлением и движком плагинов.</li>
        <li>«Пламя» считает общие дни переписки и отмечает юбилеи анимацией на весь чат.</li>
        <li>BYgram работает без регистрации устройства. Для доступа к сериям «Пламени» достаточно войти в свой аккаунт Telegram.</li>
      </ul>
      <a className="text-link" href="download.html#beta">Перейти к бета-версии <ArrowRight aria-hidden="true" /></a>
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
