// 10 промо-роликов StarTegs для TikTok. Время в секундах от начала сцены.
const A = (a, t, extra = '') => `data-a="${a}" data-t="${t}" ${extra}`;
// слова по одному с ударом
function words(text, t0, step = .16, a = 'slam', cls = '', hitEach = false) {
  let k = 0;
  return text.split('\n').map(line => `<div>${line.split(' ').map(w => {
    const t = (t0 + step * k++).toFixed(2);
    const g = w.startsWith('*') ? ' g' : '';
    w = w.replace(/^\*/, '');
    return `<span class="w${g} ${cls}" ${A(a, t, 'data-d=".3"' + (hitEach ? ' data-hit' : ''))}>${w}</span>`;
  }).join(' ')}</div>`).join('');
}
const big = (text, t0 = 0, step = .16, a = 'slam', hit = false) => `<div class="big">${words(text, t0, step, a, '', hit)}</div>`;
const emo = (e, t = 0, a = 'pop', cls = 'emo') => `<div class="${cls}" ${A(a, t)}><div ${A('float', 0)}>${e}</div></div>`;
const chip = (txt, t = 0, cls = '', a = 'down') => `<div class="chip ${cls}" ${A(a, t)}>${txt}</div>`;
const msg = (side, html, t, extra = '', tm = '') => `<div class="m ${side}" ${A('msg', t, 'data-d=".4" ' + extra)}>${html}${tm ? `<span class="tm">${tm}</span>` : ''}</div>`;
const head = (ava, name, st, avaStyle = '') => `<div class="ph-head"><div class="ava" style="${avaStyle}">${ava}</div><div><div class="nm">${name}</div><div class="st">${st}</div></div></div>`;
const phone = (inner, a = 'rise', t = 0, style = '') => `<div ${A(a, t, 'data-d=".7"')} style="${style}"><div class="phone"><div class="notch"></div><div class="scr">${inner}</div></div></div>`;
const input = (txt = 'Сообщение', extra = '') => `<div class="ph-in"><div class="f" ${extra}>${txt}</div><div class="s">➤</div></div>`;
const wave = (n = 26, seed = 3) => { const r = rnd(seed); return `<div class="wave">${Array.from({ length: n }, () => `<i style="height:${6 + r() * 24}px"></i>`).join('')}</div>` };

const CTA = (d = 3.2, line = 'Telegram, только прокачанный') => ({
  d, tr: 'zoomout', k: 'impact', html: `
  <div class="cta-logo" ${A('spinin', 0, 'data-d=".6"')}>⭐</div>
  <div class="huge g" ${A('slam', .25, 'data-d=".3" data-hit')} style="font-size:84px">StarTegs</div>
  <div class="mid" ${A('up', .55)}>${line}</div>
  <div class="handle" ${A('pop', .85)}><span ${A('pulse', 0)} class="w">🤖 @StarTegsEcosistem_bot</span></div>
  <div class="row" ${A('up', 1.1)}><span class="chip">🤖 Android — мод</span><span class="chip">🍏 iPhone — Mini App</span></div>
  <div class="small" ${A('fade', 1.35)}>Ссылка в профиле 👆</div>
  <div class="disc" ${A('fade', 1.5)}>Неофициальный клиент. Не связан с Telegram FZ-LLC.</div>`
});

const VIDEOS = {
  // 1 — Удалённые сообщения
  '01': {
    title: 'Удалённые сообщения', palette: 'violet', seed: 11, music: { bpm: 128, style: 'house', root: 45, prog: [0, -4, 3, -2] },
    scenes: [
      { d: 1.9, html: `${emo('🫣', 0)}${big('Она *удалила\nсообщение', .1, .14, 'slam', true)}` },
      { d: 3.7, tr: 'whip', html: `${chip('😐 Обычный Telegram', 0)}
        ${phone(head('Л', 'Лиза 💜', 'в сети') + `<div class="msgs">
          ${msg('out', 'ты чего молчишь?', .3, '', '23:41')}
          ${msg('in', 'слушай...', .8, '', '23:42')}
          ${msg('in', 'я всё ещё думаю о тебе 🥺', 1.3, 'data-o="2.4" data-od=".5" data-collapse', '23:42')}
          <div class="m in" ${A('show', 2.9)} style="font-style:italic;color:#8f95ad;background:transparent">Лиза удалила сообщение</div>
        </div>` + input(), 'rise', .1)}` },
      { d: 1.4, tr: 'glitch', html: `${big('А теперь\n*StarTegs', 0, .14)}${emo('👀', .35)}` },
      { d: 3.8, tr: 'whip', pal: 'violet', html: `${chip('⭐ StarTegs', 0, 'acc')}
        ${phone(head('Л', 'Лиза 💜', 'в сети') + `<div class="msgs">
          ${msg('out', 'ты чего молчишь?', 0, '', '23:41')}
          ${msg('in', 'слушай...', 0, '', '23:42')}
          <div class="m in" ${A('cls', 1.0, 'data-c="del"')}><span ${A('msg', 0)} style="display:inline">я всё ещё думаю о тебе 🥺</span><span class="tag" ${A('pop', 1.0, 'data-hit')}>🗑 удалено · но мы всё видели</span></div>
          ${msg('in', 'забей, я пошутила <span class="tag e">✏️ изменено · было: «давай встретимся»</span>', 2.0, 'data-hit')}
        </div>` + input(), 'fade', 0)}` },
      { d: 2.0, html: `${big('Удалила?\n*Мы сохранили', 0, .16, 'slam', true)}<div class="row" ${A('up', .7)}>${chip('🗑 удалённые')}${chip('✏️ история правок')}${chip('🔎 поиск')}</div>` },
      CTA(),
    ]
  },

  // 2 — Режим призрака
  '02': {
    title: 'Режим призрака', palette: 'neon', seed: 22, music: { bpm: 140, style: 'trap', root: 42, prog: [0, 0, -2, -5] },
    scenes: [
      { d: 1.8, html: `${emo('👻', 0)}${big('Как читать\nи *не спалиться', .1, .13, 'slam', true)}` },
      { d: 3.9, tr: 'whip', html: `${chip('🕶 Приватность StarTegs', 0, 'acc')}
        <div class="card" ${A('rise', .1, 'data-d=".6"')} style="width:410px">
          <div class="tg" ${A('cls', .8)}><span class="em">⌨️</span>Скрыть «печатает…»<div class="sw" ${A('var', .8, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 1.4)}><span class="em">🟢</span>Не показывать «в сети»<div class="sw" ${A('var', 1.4, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 2.0)}><span class="em">✓</span>Не отправлять «прочитано»<div class="sw" ${A('var', 2.0, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 2.6)}><span class="em">👁</span>Тайный просмотр историй<div class="sw" ${A('var', 2.6, 'data-d=".2"')}></div></div>
        </div>` },
      { d: 3.6, tr: 'whip', html: `
        ${phone(head('М', 'Макс', 'был(а) недавно', 'background:linear-gradient(135deg,#00f0ff,#3d6bff)') + `<div class="msgs">
          ${msg('in', 'ты видел мой пост?', .2, '', '14:02')}
          ${msg('in', 'ау 👀', .6, '', '14:03')}
          ${msg('in', 'ну ответь хоть что-то 😭', 1.0, '', '14:05')}
        </div>` + input(), 'rise', 0)}
        ${chip('✓ не прочитано · а ты всё прочитал 😏', 1.6, 'acc', 'pop')}` },
      { d: 2.0, html: `${big('Ты — *призрак', 0, .2, 'slam', true)}${emo('👻', .5, 'spinin')}` },
      CTA(3.2, 'Читай как призрак 👻'),
    ]
  },

  // 3 — Двойное дно
  '03': {
    title: 'Двойное дно', palette: 'danger', seed: 33, music: { bpm: 100, style: 'trap', root: 40, prog: [0, 1, -4, -2] },
    scenes: [
      { d: 2.0, html: `${emo('😰', 0)}${big('Друг попросил\n*твой телефон', .1, .14, 'slam', true)}` },
      { d: 3.6, tr: 'whip', html: `${chip('🙈 Скрыть чат из списка', 0, 'acc')}
        ${phone(`<div class="ph-head"><div class="nm" style="font-size:20px">Чаты</div></div><div class="list">
          <div class="li"><div class="ava" style="background:linear-gradient(135deg,#ffb627,#ff6b3d)">М</div><div class="t"><div class="n">Мама ❤️</div><div class="p">Ты поел?</div></div></div>
          <div class="li"><div class="ava" style="background:linear-gradient(135deg,#19e3a1,#3dd6ff)">9</div><div class="t"><div class="n">9Б класс 📚</div><div class="p">Кто сделал алгебру?</div></div><div class="b">12</div></div>
          <div class="li" ${A('fade', 0, 'data-o="1.4" data-od=".45" data-collapse')} style="background:rgba(255,59,92,.15)"><div class="ava">🔥</div><div class="t"><div class="n">Секретный чат 🤫</div><div class="p">завтра всё в силе?</div></div><div class="b">3</div></div>
          <div class="li"><div class="ava" style="background:linear-gradient(135deg,#9b5cff,#5ce1ff)">П</div><div class="t"><div class="n">Папа</div><div class="p">Купи хлеб</div></div></div>
          <div class="li"><div class="ava" style="background:linear-gradient(135deg,#ff4d6d,#ffd166)">Р</div><div class="t"><div class="n">Репетитор</div><div class="p">Домашка на четверг</div></div></div>
        </div>`, 'rise', .1)}
        <div ${A('pop', 1.9, 'data-hit')} class="chip red">✨ исчез. совсем.</div>` },
      { d: 3.4, tr: 'whip', html: `${chip('🎭 Фальшивый код-пароль', 0, 'acc')}
        <div class="card" ${A('rise', .1, 'data-d=".6"')} style="width:340px;text-align:center">
          <div class="small" style="margin-bottom:6px">Введите код-пароль</div>
          <div class="dots"><i ${A('cls', .6)}></i><i ${A('cls', .9)}></i><i ${A('cls', 1.2)}></i><i ${A('cls', 1.5)}></i></div>
          <div class="kbd">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map(n => `<div>${n}</div>`).join('')}</div>
        </div>
        <div class="mid" ${A('pop', 1.9, 'data-hit')}>Открылся <span class="g">«чистый»</span> Telegram 😇</div>` },
      { d: 3.2, tr: 'whip', html: `${chip('🔎 Секретное слово в поиске', 0, 'acc')}
        <div class="card" ${A('up', .1)} style="width:400px;font:600 22px Inter">🔍 <span ${A('type', .3, 'data-cps="10"')}>пицца</span></div>
        <div class="emo" ${A('pop', 1.1, 'data-hit')} style="font-size:90px">👆</div>
        <div class="mid" ${A('up', 1.4)}>Отпечаток → <span class="g">скрытые чаты</span> 🔓</div>` },
      { d: 2.0, html: `${big('Никто\n*не узнает', 0, .18, 'slam', true)}${emo('🤫', .5)}` },
      CTA(3.2, 'Telegram с двойным дном'),
    ]
  },

  // 4 — ИИ
  '04': {
    title: 'ИИ в Telegram', palette: 'emerald', seed: 44, music: { bpm: 124, style: 'house', root: 47, prog: [0, -3, -5, -1] },
    scenes: [
      { d: 2.1, html: `<div class="num g" ${A('count', 0, 'data-to="347" data-d="1.3"')}>0</div>${big('непрочитанных\nв чате класса', .2, .14, 'up')}${emo('😵‍💫', 1.2, 'pop')}` },
      { d: 4.2, tr: 'whip', html: `${chip('⋮ → 🧠 «Что я пропустил?»', 0, 'acc')}
        <div class="card" ${A('rise', .2, 'data-d=".6"')} style="width:430px">
          <div style="font:800 18px Inter;margin-bottom:10px">🤖 Коротко за сегодня:</div>
          <div style="font:500 17px/1.45 Inter,'Noto Color Emoji'" ${A('type', .8, 'data-cps="38"')}>📚 Контрольную перенесли на пятницу. 🎬 Саша зовёт всех в кино в 18:00. 🔔 Тебя отметили 2 раза — спрашивают про проект.</div>
        </div>
        ${chip('347 сообщений → 3 строчки ⚡', 3.3, '', 'pop')}` },
      { d: 3.6, tr: 'whip', html: `${chip('🎙 Расшифровка голосовых', 0, 'acc')}
        <div class="card" ${A('rise', .1, 'data-d=".5"')} style="width:420px;color:var(--a1)">
          <div class="row" style="justify-content:flex-start;gap:12px"><div class="ava" style="background:var(--grad)">▶</div>${wave(28, 5)}<span class="small">4:37</span></div>
          <div style="margin-top:12px;color:#fff;font:500 17px/1.4 Inter,'Noto Color Emoji'" ${A('type', 1.0, 'data-cps="34"')}>«Короче, я опоздаю минут на 10, захвати мне шаурму, потом скину деньги 🙏»</div>
        </div>
        <div class="mid" ${A('pop', 2.8, 'data-hit')}>4 минуты слушать? <span class="g">Нет.</span></div>` },
      { d: 3.6, tr: 'whip', html: `${chip('✨ «Улучшить мой текст»', 0, 'acc')}
        <div class="card" ${A('up', .1)} style="width:420px;font:600 20px Inter"><span class="strike" ${A('var', .9, 'data-d=".35"')}>ты чо игнориш</span></div>
        <div class="emo" style="font-size:54px" ${A('pop', 1.1)}>⬇️</div>
        <div class="card" ${A('pop', 1.3, 'data-hit')} style="width:420px;font:600 19px/1.4 Inter,'Noto Color Emoji'">Привет! Ты, наверное, занят — ответь, как будет минутка 🙂</div>` },
      { d: 2.0, html: `${big('ИИ прямо\n*в чате', 0, .18, 'slam', true)}${emo('🤖', .5)}` },
      CTA(3.2, 'Нейросеть внутри Telegram'),
    ]
  },

  // 5 — Без рекламы
  '05': {
    title: 'Без рекламы', palette: 'neon', seed: 55, music: { bpm: 132, style: 'house', root: 43, prog: [0, 5, 3, -2] },
    scenes: [
      { d: 2.0, html: `${emo('🤮', 0)}${big('Реклама\nв каналах\n*достала?', .1, .13, 'slam', true)}` },
      { d: 4.0, tr: 'whip', html: `${chip('🚫 StarTegs чистит ленту', 0, 'acc')}
        <div class="col" style="width:420px;gap:10px">
          <div class="card" ${A('right', .2)} style="width:100%;font:600 16px Inter">📰 Новый выпуск — разбор недели</div>
          <div class="card" ${A('left', .4, 'data-o="1.3" data-od=".35" data-collapse')} style="width:100%;font:800 17px Inter;border-color:#ff3b5c"><span class="chip red" style="font-size:12px;padding:4px 10px">Реклама</span><br><span class="strike" ${A('var', .9, 'data-d=".3"')}>🔥 КУРС «КАК СТАТЬ МИЛЛИОНЕРОМ» — 99 990 ₽</span></div>
          <div class="card" ${A('right', .6)} style="width:100%;font:600 16px Inter">🎮 Анонс обновления игры</div>
          <div class="card" ${A('left', .8, 'data-o="2.2" data-od=".35" data-collapse')} style="width:100%;font:800 17px Inter;border-color:#ff3b5c"><span class="chip red" style="font-size:12px;padding:4px 10px">Спонсор</span><br><span class="strike" ${A('var', 1.8, 'data-d=".3"')}>💸 ЗАРАБОТОК 300К В МЕСЯЦ БЕЗ ВЛОЖЕНИЙ</span></div>
          <div class="card" ${A('right', 1.0)} style="width:100%;font:600 16px Inter">😂 Мем дня</div>
        </div>
        ${chip('✨ скрыто StarTegs', 2.6, 'acc', 'pop')}` },
      { d: 3.6, tr: 'whip', html: `<div class="mid" ${A('up', 0)}>Одна кнопка — и:</div>
        <div class="card" style="width:430px" ${A('rise', .1, 'data-d=".6"')}>
          <div class="tg" ${A('cls', .7)}><span class="em">🚫</span>Спонсорские посты<div class="sw" ${A('var', .7, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 1.2)}><span class="em">🔤</span>Посты с рекламными словами<div class="sw" ${A('var', 1.2, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 1.7)}><span class="em">👮</span>Админы канала без прав<div class="sw" ${A('var', 1.7, 'data-d=".2"')}></div></div>
          <div class="tg" ${A('cls', 2.2)}><span class="em">🌐</span>Умные прокси, авто-обновление<div class="sw" ${A('var', 2.2, 'data-d=".2"')}></div></div>
        </div>` },
      { d: 1.9, html: `${big('Чисто.', 0, .2, 'slam', true)}${emo('🧼', .3)}` },
      CTA(3.2, 'Лента без мусора'),
    ]
  },

  // 6 — Дизайн
  '06': {
    title: 'Дизайн', palette: 'violet', seed: 66, music: { bpm: 120, style: 'house', root: 48, prog: [0, -5, -3, -7] },
    scenes: [
      { d: 2.0, html: `${big('Твой Telegram\n*скучный?', 0, .15, 'slam', true)}<div ${A('pop', .6)} style="filter:grayscale(1) brightness(.8)" class="emo">😴</div>` },
      ...[['violet', 'Фиолет 💜'], ['neon', 'Неон 💙'], ['gold', 'Золото 💛'], ['emerald', 'Изумруд 💚'], ['sunset', 'Закат 🧡']].map(([pal, nm], i) => ({
        d: 1.0, pal, tr: i ? 'glitch' : 'whip', k: 'hit', html: `${chip(nm, 0, 'acc', 'pop')}
        ${phone(head('А', 'Аня ✨', 'в сети', 'background:var(--grad)') + `<div class="msgs">
          <div class="m in">смотри что у меня 😍</div>
          <div class="m out">ВАУ это что за тема??</div>
          <div class="m in">StarTegs, палитра «${nm.split(' ')[0]}»</div>
          <div class="m out">ставлю себе 🔥</div>
        </div>` + input(), i ? 'show' : 'rise', 0)}`
      })),
      { d: 3.2, tr: 'whip', pal: 'neon', html: `<div class="col" style="gap:14px">
        ${['🌈 Градиентные пузыри', '🌃 Неоновый OLED', '🫧 Стеклянные панели', '🔤 7 шрифтов', '❄️ Снег над чатами', '🖼 Тема по аватарке'].map((t, i) => `<div class="mid" ${A(i % 2 ? 'right' : 'left', .15 + i * .38, 'data-hit')}>${t}</div>`).join('')}
      </div>` },
      { d: 2.0, pal: 'sunset', html: `${big('Сделай\n*своим', 0, .18, 'slam', true)}${emo('✨', .5)}` },
      CTA(3.2, 'Telegram, который выглядит как ты'),
    ]
  },

  // 7 — С друзьями
  '07': {
    title: 'С друзьями', palette: 'sunset', seed: 77, music: { bpm: 150, style: 'trap', root: 44, prog: [0, -4, 3, 5] },
    scenes: [
      { d: 2.2, html: `<div class="row" style="gap:6px" ${A('pop', 0)}><span class="emo" style="font-size:120px">🔥</span><span class="num g" ${A('count', .1, 'data-to="47" data-d="1"')}>0</span></div>${big('дней подряд\nвы пишете друг другу', .5, .1, 'up')}` },
      { d: 3.6, tr: 'whip', html: `${chip('🔥 Стрики + рамки на аватарку', 0, 'acc')}
        ${phone(`<div class="ph-head"><div class="nm" style="font-size:20px">Чаты</div></div><div class="list">
          ${[['Даня', '🔥 47', '0 0 0 3px #ff9e40,0 0 18px #ff4d6d', 'го в кс?'], ['Вика', '🔥 112', '0 0 0 3px #00f0ff,0 0 18px #ff2bd6', 'ахахах это я 😭'], ['Тёма', '🔥 9', '0 0 0 3px #ffd166,0 0 14px #ffb627', '👑 король чата'], ['Лиза', '🔥 23', '0 0 0 3px #9b5cff,0 0 18px #9b5cff', 'спокойной 🌙'], ['Кира', '🔥 5', '0 0 0 3px #19e3a1', 'завтра в 10?']].map(([n, s, sh, p], i) => `
          <div class="li" ${A('left', .3 + i * .22)}><div class="ava" style="box-shadow:${sh}">${n[0]}</div><div class="t"><div class="n">${n} <span style="font-size:12.5px;color:#ffb36b">${s}</span></div><div class="p">${p}</div></div></div>`).join('')}
        </div>`, 'rise', .05)}` },
      { d: 3.0, tr: 'whip', html: `${chip('🎉 Эффекты при отправке', 0, 'acc')}
        <div style="position:relative">${phone(head('В', 'Вика', 'в сети', 'background:var(--grad)') + `<div class="msgs">
          ${msg('in', 'я сдала экзамен!!! 😭😭', .2)}
          ${msg('out', 'ПОЗДРАВЛЯЮЮЮ 🎉🎉🎉', .9, 'data-hit')}
        </div>` + input(), 'fade', 0)}
        ${(() => { const r = rnd(9); return Array.from({ length: 26 }, (_, i) => `<div ${A('fly', .95 + r() * .2, `data-d="1.4" data-x="${(r() - .5) * 520}" data-y="${-200 - r() * 420}"`)} style="position:absolute;left:${190 + r() * 60}px;top:470px;font:${26 + r() * 22}px 'Noto Color Emoji'">${['🎉', '💜', '✨', '🎊', '❤️', '⭐'][i % 6]}</div>`).join('') })()}</div>` },
      { d: 3.4, tr: 'whip', html: `<div class="row" style="gap:26px;align-items:center">
          <div class="col">${chip('🎮 Игры в чате', 0, 'acc')}<div class="ttt" ${A('pop', .2)}>${['X', 'O', 'X', '', 'X', 'O', 'O', '', 'X'].map((c, i) => `<div><span ${A('pop', .4 + i * .14)} class="w" style="color:${c === 'X' ? 'var(--a2)' : 'var(--a3)'}">${c}</span></div>`).join('')}</div></div>
        </div>
        <div class="col" style="margin-top:8px">${chip('💞 Совместимость', 1.3, 'acc')}
          <div class="ring" style="--v:87" ${A('pop', 1.4, 'data-d=".9"')}><div><div class="mid g" ${A('count', 1.5, 'data-to="87" data-suf="%" data-d=".9"')}>0</div><div class="small">на одной волне</div></div></div></div>` },
      { d: 2.0, html: `${big('Зови\n*друзей', 0, .2, 'slam', true)}${emo('💞', .5)}` },
      CTA(3.2, 'Telegram, где весело вместе'),
    ]
  },

  // 8 — Отмена отправки
  '08': {
    title: 'Отмена отправки', palette: 'violet', seed: 88, music: { bpm: 96, style: 'trap', root: 41, prog: [0, -4, -2, -5], dropAt: 6.0 },
    scenes: [
      { d: 2.2, html: `<div class="huge" ${A('blur', 0, 'data-d=".6"')} style="font-size:120px">03:12</div>${big('ты пишешь\n*бывшей', .6, .18, 'up')}${emo('🥀', 1.1)}` },
      { d: 3.8, tr: 'whip', hit: false, html: `
        ${phone(head('Б', 'Бывшая', 'была давно', 'background:#555') + `<div class="msgs">
          ${msg('in', 'удачи тебе 🙂', 0, '', 'июль')}
          <div class="m out" ${A('msg', 1.6, 'data-d=".35" data-o="3.0" data-od=".35" data-collapse')}>я скучаю по тебе... 🥺<span class="tag" style="color:#fff;opacity:.9">⏳ отправится через <span ${A('count', 1.6, 'data-from="5" data-to="2" data-d="1.4"')}>5</span> с</span></div>
        </div>` + `<div class="ph-in"><div class="f"><span ${A('type', .2, 'data-cps="16" data-o="1.55" data-od=".05"')}>я скучаю по тебе... 🥺</span></div><div class="s">➤</div></div>`, 'rise', 0)}
        <div class="chip red" ${A('pop', 2.1)} style="font-size:24px;padding:14px 26px"><span ${A('pulse', 0)} class="w">↩️ ОТМЕНИТЬ</span></div>
        <div class="emo" ${A('pop', 2.75, 'data-hit')} style="position:absolute;top:640px;left:290px;font-size:60px">👆</div>` },
      { d: 2.0, k: 'impact', html: `${big('Фух.', 0, .2, 'slam', true)}${emo('😮‍💨', .3)}<div class="mid" ${A('up', .6)}>Сообщение <span class="g">не ушло</span></div>` },
      { d: 3.8, tr: 'whip', html: `${chip('😬 Проверка тона', 0, 'acc')}
        <div class="card" ${A('up', .1)} style="width:420px;font:600 20px Inter,'Noto Color Emoji'"><span ${A('type', .3, 'data-cps="22"')}>ты вообще нормальный??</span></div>
        <div class="card" ${A('pop', 1.5, 'data-hit')} style="width:420px;border-color:#ffb627"><div style="font:800 17px Inter;color:#ffc46b">⚠️ Звучит резко. Смягчить?</div><div style="font:500 17px/1.4 Inter,'Noto Color Emoji';margin-top:8px" ${A('type', 2.1, 'data-cps="34"')}>Мне немного обидно, давай обсудим спокойно 🙏</div></div>` },
      { d: 2.2, html: `${big('StarTegs\n*спасает\nрепутацию', 0, .18, 'slam', true)}` },
      CTA(3.2, 'Telegram, который думает за тебя'),
    ]
  },

  // 9 — Итоги года
  '09': {
    title: 'Итоги года', palette: 'sunset', seed: 99, music: { bpm: 128, style: 'house', root: 46, prog: [0, -5, -3, -1] },
    scenes: [
      { d: 2.0, html: `${big('Твой год\nв Telegram', 0, .15, 'slam')}<div class="huge g" ${A('slam', .55, 'data-hit')} style="font-size:150px">2026</div>` },
      { d: 2.4, tr: 'whip', pal: 'violet', html: `${chip('✍️ Отправлено за год', 0, 'acc')}<div class="num g" style="font-size:96px" ${A('count', .2, 'data-to="48392" data-d="1.4"')}>0</div><div class="mid" ${A('up', .6)}>сообщений</div><div class="small" ${A('fade', 1.4)}>это ~132 в день 😳</div>` },
      { d: 2.4, tr: 'whip', pal: 'neon', html: `${chip('💬 Главный собеседник', 0, 'acc')}<div class="ava" ${A('spinin', .2, 'data-d=".6"')} style="width:150px;height:150px;font-size:64px;box-shadow:0 0 0 5px var(--a1),0 0 60px var(--glow);background:var(--grad)">Л</div><div class="big" ${A('slam', .6, 'data-hit')}>Лиза 💜</div><div class="mid g" ${A('count', .8, 'data-to="12840" data-suf=" сообщений" data-d="1"')}>0</div>` },
      { d: 2.4, tr: 'whip', pal: 'gold', html: `${chip('🗣 Слово года', 0, 'acc')}<div class="huge" ${A('slam', .2, 'data-hit')} style="font-size:84px">«короче»</div><div class="small" ${A('fade', .6)}>2 314 раз. Короче, много.</div>` },
      { d: 2.4, tr: 'whip', pal: 'emerald', html: `${chip('😭 Эмодзи года', 0, 'acc')}<div class="emo" ${A('spinin', .15, 'data-d=".6" data-hit')} style="font-size:200px">😭</div><div class="mid" ${A('up', .6)}><span ${A('count', .6, 'data-to="5021" data-d=".9"')}>0</span> раз</div>` },
      { d: 2.4, tr: 'whip', pal: 'violet', html: `${chip('🌙 Любимое время', 0, 'acc')}<div class="num g" ${A('slam', .2, 'data-hit')}>01:47</div><div class="mid" ${A('up', .6)}>спать? не слышали</div>` },
      { d: 2.0, pal: 'sunset', html: `${big('А какие\n*твои итоги?', 0, .16, 'slam', true)}${emo('🎁', .5)}` },
      CTA(3.2, 'Итоги года — в один тап'),
    ]
  },

  // 10 — Топ-10 фишек
  '10': {
    title: 'Топ-10 фишек', palette: 'violet', seed: 101, music: { bpm: 150, style: 'house', root: 45, prog: [0, -4, 3, -2] },
    scenes: [
      { d: 1.6, html: `${big('10 фишек\nза *15 секунд', 0, .12, 'slam', true)}${emo('⏱', .5)}` },
      ...[['🗑', 'Удалённые сообщения', 'violet'], ['👻', 'Режим призрака', 'neon'], ['🔒', 'Скрытые чаты по PIN', 'danger'], ['🤖', 'ИИ в каждом чате', 'emerald'], ['🎙', 'Голосовые → текст', 'gold'],
        ['🚫', 'Без рекламы', 'neon'], ['↩️', 'Отмена отправки', 'sunset'], ['👥', 'До 10 аккаунтов', 'violet'], ['🔥', 'Стрики и эффекты', 'sunset'], ['🎁', 'Итоги года', 'gold']].map(([e, t, pal], i) => ({
          d: 1.2, pal, tr: i % 2 ? 'glitch' : 'punch', html: `<div class="num g" style="font-size:150px" ${A('slam', 0, 'data-d=".25"')}>${10 - i}</div><div class="emo" ${A('pop', .1)}>${e}</div><div class="big" style="font-size:48px" ${A('up', .2, 'data-d=".3"')}>${t}</div>`
        })),
      { d: 1.8, pal: 'violet', html: `${big('И это\n*не всё', 0, .16, 'slam', true)}<div class="small" ${A('fade', .6)}>и ещё десятки функций внутри</div>` },
      CTA(3.2, 'Сохрани, чтобы не потерять 📌'),
    ]
  },
};
window.VIDEOS = VIDEOS;
