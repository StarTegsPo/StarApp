/*
 * StarTegs 4.2 — «Все разделы»: настройки мода по разделам, как в больших клиентах-модах.
 * 14 разделов: Premium, Приватность, Двойное дно, Чаты, Сообщения, Отправка, Медиа, ИИ,
 * Плагины, Оформление, Бизнес, Друзья и игры, Реклама и каналы, Прочее.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

public final class StarSections {

    private StarSections() {}

    static final String[][] SECTIONS = {
            {"⭐", "Premium", "пробный период, функции Premium"},
            {"👻", "Приватность", "призрак 2.0: расписание, по чатам, таймер"},
            {"🕳", "Двойное дно", "скрытые чаты, PIN, фальшивый и тревожный код"},
            {"💬", "Чаты", "метки, заметки, фокус, фильтр, поиск"},
            {"✉️", "Сообщения", "удалённые 2.0, «было → стало», 🧠 память"},
            {"📤", "Отправка", "отмена, тон, подтверждение, без звука"},
            {"🖼", "Медиа", "загрузка, истории, кружки, скачать всё"},
            {"🤖", "Нейросеть", "чат с ИИ, перевод, «Ответь за меня»"},
            {"🧩", "Плагины", "команды .calc, .tr, .ai и свои"},
            {"🎨", "Оформление", "магазин, темы, значки, иконки"},
            {"💼", "Бизнес", "кабинет, «Нет на месте», приветствие"},
            {"🎉", "Развлечения и друзья", "питомец, гороскоп, колесо, игры, друзья"},
            {"📢", "Каналы и реклама", "лента каналов, фильтр постов, реклама, прокси"},
            {"⚙️", "Прочее", "резервная копия, очистка, обновления"},
            {"📚", "Продуктивность", "дела, прочитать позже, капсула, экранное время"},
            {"🛡", "Безопасность", "устройства, новые входы, аварийный жест, фишинг"},
            {"💰", "Кошелёк", "баланс 💎, переводы, колесо бонусов, поддержать"},
            {"🌐", "Сервисы экосистемы", "боты StarTegs: архив, маркет, ИИ, аналитика"},
            {"✨", "Стиль и крутость", "красивый шрифт, имя, «О себе», скрины"},
            {"⚙️", "Ещё", "Premium, плагины, бизнес, сервисы, прочее"},
            {"🎨", "Дизайн", "конструктор темы, фон чата, кнопка, звуки, стиль для друзей"},
            {"🧰", "Удобство", "папки, тихие часы, ключевые слова, стример, чистые ссылки"},
    };

    /** Порядок разделов на главном экране. */
    static final int[] ORDER = {0, 1, 2, 3, 4, 5, 6, 7, 14, 11, 16, 9, 12, 15, 8, 10, 17, 13};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean raw(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    static boolean ghostAll() {
        return raw("ghost_typing", false) && raw("ghost_read", false) && raw("ghost_online", false) && raw("ghost_stories", false);
    }

    static void setGhostAll(boolean on) {
        prefs().edit().putBoolean("ghost_typing", on).putBoolean("ghost_read", on).putBoolean("ghost_online", on).putBoolean("ghost_stories", on).apply();
        StarGhost.arm();
    }

    static final int[][] GROUPS = {{20, 18, 9, 11, 16}, {3, 21, 4, 5, 7}, {1, 2, 15}, {14, 6, 12}, {19}};
    static final String[] GROUP_TITLES = {"🔥 Стиль и развлечения", "💬 Общение", "🔒 Приватность", "📚 Полезное", ""};

    /** Главный экран StarTegs. */
    public static class Index extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "⭐ StarTegs";
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (getParentActivity() != null) {
                String[][] btns = {{"👻", "Призрак"}, {"✨", "Шрифт"}, {"🪪", "Имя"}, {"🛍", "Магазин"}, {"🐾", "Питомец"}};
                items.add(UItem.asCustom(StarHero.create(getParentActivity(), currentAccount, btns, which -> {
                    switch (which) {
                        case 0:
                            if (!StarMod.requirePro(this, "Режим призрака")) return;
                            setGhostAll(!ghostAll());
                            android.widget.Toast.makeText(getParentActivity(), ghostAll() ? "👻 Призрак включён: тебя не видно в сети, «печатает» и «прочитано» не отправляются" : "👻 Призрак выключен", android.widget.Toast.LENGTH_LONG).show();
                            refresh();
                            break;
                        case 1: StarStyle.chooseAuto(this, this::refresh); break;
                        case 2: StarStyle.fancyName(this); break;
                        case 3: presentFragment(new StarCosmetics.Shop()); break;
                        default: StarFun.pet(this, this::refresh);
                    }
                })));
            }
            if (getParentActivity() != null) {
                items.add(UItem.asCustom(StarYou.profileCard(this, () -> presentFragment(new Screen(20)))));
            }
            items.add(UItem.asButton(905, R.drawable.msg_search, "🔎 Поиск по настройкам StarTegs"));
            if (!StarMod.isPro()) {
                items.add(UItem.asButton(99, R.drawable.msg_premium_badge, StarTrial.available() ? "🎁 Premium на 3 дня — бесплатно" : "⭐ Открыть всё с Premium").accent());
            }
            items.add(UItem.asHeader("⚡ Быстро"));
            items.add(UItem.asCheck(901, "👻 Режим призрака" + (StarMod.isPro() ? "" : "  ⭐")).setChecked(ghostAll()));
            items.add(UItem.asCheck(902, "🫥 Шторка приватности" + (StarMod.isPro() ? "" : "  ⭐")).setChecked(StarShade.enabled()));
            items.add(UItem.asCheck(903, "🔕 Отправлять без звука").setChecked(StarMedia.silentDefault()));
            items.add(UItem.asButton(904, R.drawable.msg_edit, "✨ Всегда писать красиво", StarStyle.autoStyleTitle()));
            for (int g = 0; g < GROUPS.length; g++) {
                if (!GROUP_TITLES[g].isEmpty()) items.add(UItem.asHeader(GROUP_TITLES[g]));
                else items.add(UItem.asShadow(null));
                for (int i : GROUPS[g]) items.add(StarYou.tile(100 + i, i)); // 5.0: цветные плитки
                if (g == 0 && getParentActivity() != null) {
                    items.add(UItem.asHeader("🎨 Темы одним касанием"));
                    items.add(UItem.asCustom(StarYou.gallery(this, this::refresh)));
                    items.add(UItem.asButton(906, R.drawable.msg_palette, "Пройти знакомство заново", ""));
                }
            }
            items.add(UItem.asShadow("StarTegs " + StarModConfig.MOD_VERSION + " · тариф " + StarMod.tierTitle() + ". Функции с ⭐ — в Premium."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            switch (item.id) {
                case 99:
                    if (StarTrial.available()) StarTrial.offer(this, this::refresh);
                    else presentFragment(new Screen(0));
                    return;
                case 901:
                    if (!StarMod.requirePro(this, "Режим призрака")) return;
                    setGhostAll(!ghostAll());
                    refresh();
                    return;
                case 902:
                    if (!StarMod.requirePro(this, "Шторка приватности")) return;
                    StarMod.toggle("shade_on", false);
                    StarShade.update();
                    refresh();
                    return;
                case 903:
                    StarMedia.toggle("media_silent", false);
                    refresh();
                    return;
                case 904:
                    StarStyle.chooseAuto(this, this::refresh);
                    return;
                case 905:
                    presentFragment(new Search());
                    return;
                case 906:
                    presentFragment(new StarYou.Welcome());
                    return;
            }
            int i = item.id - 100;
            if (i >= 0 && i < SECTIONS.length) presentFragment(new Screen(i));
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    /** Поиск по всем настройкам StarTegs. */
    public static class Search extends UniversalFragment {
        private String query = "";
        private final ArrayList<Object[]> index = new ArrayList<>(); // {раздел, текст}

        @Override
        protected CharSequence getTitle() {
            return "🔎 Поиск";
        }

        @Override
        public android.view.View createView(Context context) {
            android.view.View v = super.createView(context);
            org.telegram.ui.ActionBar.ActionBarMenuItem s = actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override
                        public void onTextChanged(android.widget.EditText e) {
                            query = e.getText().toString().trim().toLowerCase();
                            if (listView != null) listView.adapter.update(true);
                        }

                        @Override
                        public void onSearchCollapse() {
                            finishFragment();
                        }
                    });
            s.setSearchFieldHint("Например: призрак, шрифт, папки");
            org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> s.openSearch(true), 300);
            return v;
        }

        private void build() {
            if (!index.isEmpty()) return;
            for (int sec = 0; sec < SECTIONS.length; sec++) {
                if (sec == 19 || sec == 17) continue;
                index.add(new Object[]{sec, SECTIONS[sec][1] + " " + SECTIONS[sec][2]});
                ArrayList<UItem> tmp = new ArrayList<>();
                try {
                    new Screen(sec).fillItems(tmp, null);
                } catch (Throwable ignore) {
                }
                for (UItem it : tmp) {
                    if (it.text == null || it.viewType == UniversalAdapter.VIEW_TYPE_SHADOW) continue;
                    String t = it.text.toString().replace("  ⭐", "").trim();
                    if (t.length() > 1 && t.length() < 120) index.add(new Object[]{sec, t});
                }
            }
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            build();
            if (query.length() < 2) {
                items.add(UItem.asShadow("Введите хотя бы 2 буквы — найдём настройку в любом разделе StarTegs."));
                return;
            }
            int n = 0;
            for (int k = 0; k < index.size() && n < 60; k++) {
                Object[] e = index.get(k);
                String t = (String) e[1];
                if (!t.toLowerCase().contains(query)) continue;
                int sec = (Integer) e[0];
                items.add(UItem.asButton(10000 + k, t, SECTIONS[sec][0] + " " + SECTIONS[sec][1]));
                n++;
            }
            if (n == 0) items.add(UItem.asShadow("Ничего не нашлось. Попробуйте другое слово."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int k = item.id - 10000;
            if (k >= 0 && k < index.size()) presentFragment(new Screen((Integer) index.get(k)[0]));
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    /** Один раздел. */
    public static class Screen extends UniversalFragment {

        private final int section;

        public Screen(int section) {
            this.section = section;
        }

        @Override
        protected CharSequence getTitle() {
            return SECTIONS[section][0] + " " + SECTIONS[section][1];
        }

        private static final int[] UNDO = {0, 3, 5, 10};

        private void check(ArrayList<UItem> items, int id, String title, boolean on) {
            items.add(UItem.asCheck(id, title).setChecked(on));
        }

        private void button(ArrayList<UItem> items, int id, String title) {
            items.add(UItem.asButton(id, title, ""));
        }

        private void button(ArrayList<UItem> items, int id, String title, String value) {
            items.add(UItem.asButton(id, title, value));
        }

        private interface Pick {
            void run(int which);
        }

        private void choose(String title, String[] items, Pick cb) {
            if (getParentActivity() == null) return;
            new AlertDialog.Builder(getParentActivity()).setTitle(title).setItems(items, (d, w) -> cb.run(w)).show();
        }

        private static Integer[] toObj(int[] a) {
            Integer[] r = new Integer[a.length];
            for (int i = 0; i < a.length; i++) r[i] = a[i];
            return r;
        }

        private String pro(String s) {
            return StarMod.isPro() ? s : s + "  ⭐";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            switch (section) {
                case 0:
                    items.add(UItem.asShadow("Ваш тариф: " + StarMod.tierTitle()));
                    if (StarTrial.available()) button(items, 1, "🎁 Premium 3 дня бесплатно");
                    button(items, 2, "⭐ Все Premium-функции");
                    button(items, 3, "Оформить Premium за ⭐ Stars");
                    button(items, 4, "🧪 Что нового в StarTegs");
                    break;
                case 1:
                    check(items, 10, pro("Не показывать «печатает…»"), raw("ghost_typing", false));
                    check(items, 11, pro("Не отправлять «прочитано» в личных чатах"), raw("ghost_read", false));
                    check(items, 12, pro("Не показывать «в сети»"), raw("ghost_online", false));
                    check(items, 13, pro("Смотреть истории тайно"), raw("ghost_stories", false));
                    button(items, 14, "Сбросить «не отправлять прочитано» по чатам", StarPrivacy.noReadCount() > 0 ? String.valueOf(StarPrivacy.noReadCount()) : "");
                    check(items, 15, "⌨️ Секретная клавиатура", StarPlus.incognitoKeyboard());
                    check(items, 16, "📵 Запрет скриншотов в закрытых и скрытых чатах", StarPlus.secureLocked());
                    items.add(UItem.asShadow("«Прочитано» для отдельного чата: профиль → ⋮ → «Не отправлять «прочитано»»."));
                    items.add(UItem.asHeader("👻 Призрак 2.0"));
                    button(items, 400, pro("🎭 Готовые наборы призрака"));
                    check(items, 401, "🕐 Призрак по расписанию", StarGhost.scheduleOn());
                    button(items, 402, "Время", StarGhost.schedTitle());
                    button(items, 403, "⏱ Автовыключение", StarGhost.autoOffTitle());
                    button(items, 404, "💬 Призрак по чатам", StarGhost.CHAT_MODES[StarGhost.chatMode()] + (StarGhost.listSize() > 0 ? " · " + StarGhost.listSize() : ""));
                    check(items, 405, "↩️ «Прочитать», когда отвечаю", StarGhost.readOnReply());
                    check(items, 406, pro("🎧 Не отправлять «прослушано» у голосовых и кружков"), raw("ghost_listen", false));
                    check(items, 407, "⚠️ Предупреждать перед отправкой", StarGhost.warnOn());
                    check(items, 408, "🌙 Сразу «не в сети» после отправки", StarGhost.offlineAfter());
                    check(items, 409, "👻 Кнопка призрака над чатами", raw("ghost_button", true));
                    items.add(UItem.asShadow("«Печатает», «записывает голосовое» и «выбирает стикер» скрываются вместе. "
                            + "Отметить чат и «👁 Отметить прочитанным» — меню ⋮ в чате. Плитка «Призрак» для шторки: опусти шторку → ✏️ → перетащи плитку."));
                    break;
                case 2:
                    button(items, 20, "🙈 Скрытые чаты");
                    button(items, 21, "🔒 PIN для замков и скрытых чатов");
                    button(items, 22, "🎭 Фальшивый код-пароль", StarPrivacy.hasDecoy() ? "задан" : "");
                    button(items, 23, "🔎 Секретное слово в поиске", StarPrivacy.hasSecretWord() ? "задано" : "");
                    button(items, 24, "🚨 Тревожный код", StarPlus.hasPanic() ? "задан" : "");
                    check(items, 25, "Скрывать StarTegs из «Недавних»", StarPlus.hideRecents());
                    check(items, 26, pro("🫥 Шторка приватности"), StarShade.enabled());
                    break;
                case 3:
                    button(items, 30, "🏷 Метки чатов");
                    button(items, 31, "📝 Мои заметки");
                    button(items, 32, "🎯 Режим «Фокус»", StarFocus.active() ? StarFocus.statusText() : "");
                    button(items, 33, "⭐ Избранные сообщения");
                    button(items, 34, pro("🔎 Поиск по смыслу"));
                    button(items, 35, "✔️ Прочитать все чаты");
                    check(items, 36, "🚫 Фильтр сообщений по словам", StarMod.filterEnabled());
                    button(items, 37, "Слова для фильтра");
                    check(items, 38, "🤐 Скрывать мат звёздочками", StarPlus.censorOn());
                    check(items, 39, "🕓 Записывать историю профилей", StarPlus.profileHistoryOn());
                    break;
                case 4:
                    check(items, 40, "🗑 Сохранять удалённые сообщения", raw("save_deleted", true));
                    check(items, 41, "✏️ Сохранять историю правок", raw("save_edits", true));
                    button(items, 42, "Все удалённые сообщения");
                    check(items, 43, "Секунды во времени сообщений", raw("show_seconds", false));
                    check(items, 44, "🔁 «Повторить (+1)» в меню сообщения", StarPlus.repeatOn());
                    check(items, 45, "Скрыть кнопку «Поделиться» у сообщений", StarMedia.hideShareButton());
                    items.add(UItem.asHeader("🗑 Удалённые 2.0"));
                    button(items, 410, "🔔 «Удалил(а) сообщение»", StarVault.NOTIFY_MODES[StarVault.notifyMode()]);
                    check(items, 411, "📢 Сохранять удалённые посты каналов", StarVault.saveChannels());
                    check(items, 412, "📎 Копии фото, видео и файлов", StarVault.copyMedia());
                    button(items, 413, "🧺 Хранить удалённые", StarVault.keepTitle());
                    check(items, 414, "🔒 PIN на раздел «Удалённые»", StarVault.pinOn());
                    check(items, 417, "📊 Итоги недели: сколько сохранено", StarVault.weeklyOn());
                    items.add(UItem.asShadow("Копируются только уже загруженные файлы, одноразовые фото и видео — никогда. "
                            + "«Не сохранять удалённые в этом чате» и «Сообщать, когда здесь удалят» — меню ⋮ в чате. "
                            + "История правок показывает «было → стало»: красным — что убрали, зелёным — что добавили."));
                    items.add(UItem.asHeader("🧠 Память"));
                    button(items, 415, "🧠 Моя память", StarMemory.count() > 0 ? String.valueOf(StarMemory.count()) : "");
                    button(items, 416, "Двойной тап по сообщению", StarExtra.TAP_ACTIONS[Math.min(StarExtra.tapAction(), StarExtra.TAP_ACTIONS.length - 1)]);
                    items.add(UItem.asShadow("Долгое нажатие на сообщение → «🧠 В память», или выбери «В память» для двойного тапа. Копия остаётся, даже если сообщение удалят."));
                    break;
                case 5:
                    for (int i = 0; i < UNDO.length; i++) {
                        items.add(UItem.asRadio(50 + i, UNDO[i] == 0 ? "Отмена отправки: выключена" : "Отмена отправки: " + UNDO[i] + " с")
                                .setChecked(StarSend.undoSeconds() == UNDO[i]));
                    }
                    check(items, 55, "🔕 Отправлять без звука по умолчанию", StarMedia.silentDefault());
                    check(items, 56, "Подтверждать отправку стикеров и GIF", StarMedia.confirmStickers());
                    check(items, 57, "Подтверждать отправку голосовых", StarMedia.confirmVoice());
                    if (StarAi.enabled()) {
                        check(items, 58, "😬 Проверять тон перед отправкой (ИИ)", StarSend.toneCheck());
                        check(items, 59, "🎙 Предлагать отправить голосовое текстом", StarSend.voiceToText());
                    }
                    check(items, 60, "⏰ Напоминания из переписки", StarSend.remindDetect());
                    button(items, 61, "💬 Шаблоны ответов", StarMore.templatesCount() > 0 ? String.valueOf(StarMore.templatesCount()) : "");
                    button(items, 62, "📣 Рассылка по чатам");
                    items.add(UItem.asShadow("«Отправить, когда будет в сети» — меню ⋮ в личном чате. Шаблон можно отправить, написав «/название»."));
                    break;
                case 6:
                    check(items, 70, "🚀 Ускоренная загрузка файлов", StarPremium.fastDownload());
                    check(items, 71, "📥 «Сохранить в галерею» в чужих историях", StarPremium.saveStories());
                    check(items, 72, "📹 Кружки — сразу на основную камеру", StarMedia.roundBackCamera());
                    items.add(UItem.asShadow("📥 Скачать все фото, видео и файлы из чата: меню ⋮ в чате → «Скачать все медиа» ⭐. "
                            + "Сохранять голосовые и файлы по одному — долгое нажатие → «Сохранить в загрузки» (есть в Telegram)."));
                    break;
                case 7:
                    if (!StarAi.enabled()) {
                        items.add(UItem.asShadow("Нейросеть выключена: в startegs.properties не задан ai_bot."));
                        break;
                    }
                    button(items, 80, "🤖 Чат с ИИ");
                    button(items, 81, "☀️ Сводка дня");
                    check(items, 82, "🌍 Перевод чатов через ИИ", StarAuto.aiTranslate());
                    items.add(UItem.asShadow("💡 «Ответь за меня» и «Спросить ИИ» — долгое нажатие на сообщение. "
                            + "«Что я пропустил?», «Улучшить мой текст», «Перевести мой текст» — меню ⋮ в чате. "
                            + "«Ответь за меня»: " + StarReply.FREE_PER_DAY + " в день бесплатно, сегодня осталось " + (StarMod.isPro() ? "∞" : String.valueOf(StarReply.freeLeft())) + "."));
                    break;
                case 8:
                    check(items, 90, "🧩 Команды в чате", StarPlugins.enabled());
                    button(items, 91, "Список команд");
                    button(items, 92, "Мои команды", StarPlugins.macroCount() > 0 ? String.valueOf(StarPlugins.macroCount()) : "");
                    break;
                case 9:
                    button(items, 100, "🛍 Магазин: рамки, значки, эффекты, темы");
                    button(items, 101, "🎨 Темы от пользователей");
                    button(items, 102, "🖼 Иконка приложения");
                    button(items, 103, "Палитры, шрифты, пузыри, обои — все настройки оформления");
                    break;
                case 10:
                    button(items, 110, "💼 Бизнес-кабинет", StarBiz.on() ? "вкл" : "");
                    check(items, 111, "🌙 Автоответ «Нет на месте»", StarPremium.awayOn());
                    button(items, 112, "Текст «Нет на месте»");
                    check(items, 113, "👋 Приветствие новым собеседникам", StarPremium.greetOn());
                    button(items, 114, "Текст приветствия");
                    break;
                case 11:
                    button(items, 150, "🐾 Мой питомец", StarFun.petTitle());
                    button(items, 151, "🔮 Гороскоп дня", StarFun.sign() >= 0 ? StarFun.SIGNS[StarFun.sign()] : "");
                    button(items, 152, "🎡 Колесо бонусов", "раз в день");
                    button(items, 120, "🎮 Игры в чате");
                    button(items, 121, "💞 Лучшие друзья и стрики");
                    button(items, 122, "🤫 Анонимные вопросы");
                    button(items, 123, "🏆 Достижения · уровень " + StarAchieve.level());
                    button(items, 124, "🎁 Итоги года");
                    button(items, 125, "🪪 Визитка с QR-кодом");
                    button(items, 126, "📚 Учёба");
                    button(items, 127, "🎙 Голос в голосовых", StarVoice.title());
                    button(items, 128, "💭 Мой статус", StarProfile.statusTitle());
                    break;
                case 12:
                    button(items, 160, pro("📰 Лента каналов"));
                    button(items, 161, pro("🙈 Фильтр постов по словам"), StarGuard.channelWordsCount() > 0 ? String.valueOf(StarGuard.channelWordsCount()) : "");
                    button(items, 130, "🚫 Реклама, админы, каналы, истории, прокси");
                    items.add(UItem.asShadow("Фильтр скрывает посты каналов со словами из списка (например, «крипта», «ставки») — и в каналах, и в ленте."));
                    break;
                case 13:
                    check(items, 140, "⭐ Кнопка StarTegs над списком чатов", StarMod.showChatsButton());
                    button(items, 141, "💾 Резервная копия настроек");
                    button(items, 142, "📊 Что сохранил мод");
                    button(items, 143, "🔄 Проверить обновления");
                    button(items, 145, "Старый экран StarTegs (всё одним списком)");
                    items.add(UItem.asButton(144, "Очистить сохранённое модом", "").red());
                    break;
                case 14:
                    button(items, 170, "✅ Мои дела", StarLife.openTodos() > 0 ? String.valueOf(StarLife.openTodos()) : "");
                    {
                        org.json.JSONArray t = StarLife.todos();
                        for (int i = 0; i < t.length() && i < 30; i++) {
                            org.json.JSONObject o = t.optJSONObject(i);
                            String due = o.optInt("due") > 0 ? "до " + StarLife.fmt(o.optInt("due")) : "";
                            items.add(UItem.asCheck(1000 + i, o.optString("t")).setChecked(o.optBoolean("done")));
                            if (!due.isEmpty()) items.add(UItem.asShadow(due));
                        }
                        if (t.length() > 0) button(items, 171, "Убрать выполненные");
                    }
                    items.add(UItem.asShadow("Добавить: долгое нажатие на сообщение → «✅ В дела». Напоминание придёт в «Избранное». Долгое нажатие на дело — удалить."));
                    items.add(UItem.asHeader("📌 Прочитать позже"));
                    {
                        org.json.JSONArray l = StarLife.later();
                        for (int i = 0; i < l.length() && i < 50; i++) {
                            org.json.JSONObject o = l.optJSONObject(i);
                            items.add(UItem.asButton(2000 + i, "💬 " + o.optString("c") + "\n" + o.optString("t"), ""));
                        }
                        if (l.length() == 0) items.add(UItem.asShadow("Пусто. Долгое нажатие на сообщение или пост → «📌 Прочитать позже»."));
                        else items.add(UItem.asShadow("Нажмите — открыть, долгое нажатие — убрать."));
                    }
                    items.add(UItem.asHeader("⏱ Экранное время"));
                    button(items, 172, "Сегодня в Telegram: " + StarLife.human(StarLife.secondsOn(System.currentTimeMillis())), "за неделю");
                    for (int i = 0; i < StarLife.GOALS.length; i++) {
                        int g = StarLife.GOALS[i];
                        items.add(UItem.asRadio(180 + i, g == 0 ? "Без цели" : "Цель: не больше " + (g < 60 ? g + " мин" : g / 60 + " ч") + " в день")
                                .setChecked(StarLife.goal() == g));
                    }
                    items.add(UItem.asShadow("Меню ⋮ в чате: «📅 События из чата (ИИ)» ⭐, «⏳ Капсула времени», «📝 Стёртые черновики». Фокус-режим — в разделе «Чаты»."));
                    break;
                case 15:
                    button(items, 190, "🛡 Устройства аккаунта");
                    check(items, 191, "Предупреждать о новом входе", StarGuard.loginAlerts());
                    check(items, 192, pro("📳 Аварийный жест: встряхнуть — закрыть StarTegs"), raw("guard_shake", false));
                    check(items, 193, "⚠️ Метка на подозрительных сообщениях и ссылках", StarExtra.guardEnabled());
                    items.add(UItem.asShadow("Аварийный жест: встряхните телефон 3 раза — StarTegs закроется на код-пароль Telegram (если он включён) и уйдёт с экрана. "
                            + "Фишинг: ссылки на поддельные домены Telegram и ссылки вида xn-- помечаются ⚠️. Тревожный код и шторка — в «Двойном дне»."));
                    break;
                case 16:
                    button(items, 200, "💰 Баланс и пополнение");
                    button(items, 152, "🎡 Колесо бонусов", "раз в день");
                    button(items, 201, "🫶 Поддержать StarTegs — значок навсегда");
                    items.add(UItem.asShadow("Перевести 💎 другу: чат с ним → ⋮ → «💎 Перевести кристаллы». Чаевые автору темы: магазин тем → долгое нажатие на тему. Нужен бот v62."));
                    break;
                case 18:
                    button(items, 210, "✨ Всегда писать красивым шрифтом", StarStyle.autoStyleTitle());
                    button(items, 211, "🪪 Красивое имя в профиле");
                    button(items, 212, "✍️ Крутое «О себе» от ИИ");
                    button(items, 213, "🌟 Моя витрина: настроение, песня, эмодзи");
                    button(items, 128, "💭 Мой статус", StarProfile.statusTitle());
                    button(items, 100, "🛍 Рамки, значки и эффекты");
                    items.add(UItem.asShadow("Красивый шрифт, имя и «О себе» видят ВСЕ — даже без StarTegs. "
                            + "Шрифт для одного сообщения: меню ⋮ в чате → «✨ Красивый шрифт». "
                            + "Скрин сообщения для истории: долгое нажатие → «📸 Красивый скрин». "
                            + "«Ответь за меня» теперь отвечает уверенно, дерзко или мило 😎"));
                    break;
                case 20:
                    if (getParentActivity() != null) {
                        items.add(UItem.asHeader("Готовые темы"));
                        items.add(UItem.asCustom(StarYou.gallery(this, () -> {
                            if (listView != null) listView.adapter.update(true);
                        })));
                    }
                    button(items, 220, pro("🎨 Конструктор темы с предпросмотром"));
                    items.add(UItem.asHeader("Чат"));
                    for (int i = 0; i < StarDecor.BG.length; i++) {
                        items.add(UItem.asRadio(230 + i, "Фон: " + StarDecor.BG[i] + (i >= 2 && !StarMod.isPro() ? "  ⭐" : "")).setChecked(StarDecor.bg() == i));
                    }
                    check(items, 221, "🎉 Эмодзи-дождь по словам и пасхалки", StarDecor.rainOn());
                    items.add(UItem.asShadow("«С днём рождения» — конфетти, «люблю» — сердечки, «ура» — звёзды. А ещё в StarTegs спрятаны пасхалки: найдено "
                            + StarDecor.eggsFound() + " из " + StarDecor.eggsTotal() + ". Фон применяется к вновь открытым чатам."));
                    items.add(UItem.asHeader("Список чатов"));
                    check(items, 222, "Цветная полоска у закреплённых и отмеченных чатов", StarDecor.stripeOn());
                    items.add(UItem.asShadow("Эмодзи вместо аватарки любого чата: меню ⋮ в чате → «😀 Эмодзи-аватарка» (видишь только ты)."));
                    items.add(UItem.asHeader("Интерфейс"));
                    check(items, 223, "⭐ Плавающая кнопка StarTegs со Звёздочкой (ИИ)", StarDecor.bubbleOn());
                    button(items, 224, "📳 Вибро при отправке", StarDecor.HAPTICS[StarDecor.haptic()]);
                    button(items, 225, "🔊 Звук отправки", StarDecor.sendSoundIndex() < 0 ? "как в Telegram" : (String) StarCosmetics.SOUNDS[StarDecor.sendSoundIndex()][0]);
                    check(items, 226, "🧘 Минимализм: спрятать всё лишнее", StarDecor.minimal());
                    check(items, 227, "▶️ «Сейчас играет» — музыка из Telegram в витрине", StarDecor.musicStatus());
                    items.add(UItem.asHeader("👀 Видят друзья со StarTegs"));
                    button(items, 228, pro("🖍 Цвет имени"), StarDecor.NAME_COLOR_TITLES[Math.max(0, java.util.Arrays.asList(toObj(StarDecor.NAME_COLORS)).indexOf(StarDecor.nameColor()))]);
                    button(items, 229, pro("✨ Шрифт имени"), StarDecor.nameStyle() < 0 ? "обычный" : StarStyle.STYLES[StarDecor.nameStyle()].split(" ")[0]);
                    button(items, 240, "🖼 Обложка витрины", StarDecor.COVERS[Math.max(0, StarDecor.cover())]);
                    button(items, 241, "🎂 Мой день рождения", StarDecor.birthday().isEmpty() ? "" : StarDecor.birthday().substring(3) + "." + StarDecor.birthday().substring(0, 2));
                    items.add(UItem.asShadow("Ещё друзья видят медаль уровня у твоего имени: 🥉 → 🥈 → 🥇 → 💎 → 👑 (растёт от достижений). "
                            + "Цвет чата для двоих и обои для отдельного чата уже есть в самом Telegram: меню ⋮ в чате → «Изменить цвета» / «Обои». "
                            + "Ночная тема по расписанию — Настройки → Оформление → Ночная тема."));
                    break;
                case 21:
                    button(items, 300, "📂 Папки по типам чатов", "в один тап");
                    items.add(UItem.asShadow("Личные, группы, каналы, боты и непрочитанные — настоящие папки Telegram, работают на всех устройствах."));
                    check(items, 301, "⌨️ Прятать клавиатуру при прокрутке чата", StarPro.hideKeyboardOnScroll());
                    check(items, 302, "⛔ Не переходить к следующему каналу свайпом", StarPro.noNextChannel());
                    check(items, 303, "📞 Спрашивать перед звонком", StarPro.confirmCalls());
                    check(items, 304, "🔍 «Подробности» в меню сообщения", StarPro.detailsOn());
                    items.add(UItem.asShadow("«Подробности»: ID, точное время с секундами, кто переслал, размер файла и дата-центр. Долгое нажатие на сообщение."));
                    check(items, 305, "🔋 Экономия батареи для анимаций StarTegs", StarPro.batterySaver());
                    items.add(UItem.asShadow("Если включено, рамки, частицы и эмодзи-дождь замирают, когда в Telegram включено энергосбережение (Настройки → Энергосбережение)."));
                    items.add(UItem.asHeader("Уведомления"));
                    check(items, 306, "🌙 Тихие часы: без звука и вибрации", StarX.quietOn());
                    button(items, 307, "Время тишины", StarX.quietTitle());
                    check(items, 308, "🔔 Уведомлять о ключевых словах", StarX.keywordsOn());
                    button(items, 309, "Слова", StarX.keywordsCount() > 0 ? String.valueOf(StarX.keywordsCount()) : "");
                    items.add(UItem.asShadow("Ключевые слова: если в заглушённой группе напишут твоё имя или слово из списка, придёт отдельное уведомление. Видишь только ты."));
                    items.add(UItem.asHeader("Приватность и полезное"));
                    check(items, 310, "🧹 Чистые ссылки: убирать utm, fbclid, si=", StarX.cleanLinksOn());
                    check(items, 311, "🎥 Режим стримера: скрыть номер телефона", StarX.streamer());
                    button(items, 312, "😴 Таймер сна для музыки", StarX.sleepTitle());
                    items.add(UItem.asShadow("Режим стримера прячет цифры номера в настройках и профилях — можно спокойно делать скрины и стримы. "
                            + "В чате, меню ⋮: «⌨️ Исправить раскладку» (ghbdtn → привет) и «📄 Сохранить переписку в файл»."));
                    break;
                case 19:
                    for (int k : new int[]{0, 8, 10, 17, 13}) items.add(StarYou.tile(5000 + k, k));
                    break;
                default:
                    if (StarModConfig.SERVICES.length == 0) items.add(UItem.asShadow("Сервисы не настроены в startegs.properties."));
                    for (int i = 0; i < StarModConfig.SERVICES.length; i++) {
                        String[] sv = StarModConfig.SERVICES[i];
                        if (sv[2].isEmpty()) continue;
                        items.add(UItem.asButton(3000 + i, sv[0], sv[1]));
                    }
            }
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            Runnable refresh = () -> {
                if (listView != null) listView.adapter.update(true);
            };
            int id = item.id;
            if (id >= 50 && id < 50 + UNDO.length) {
                StarSend.setUndoSeconds(UNDO[id - 50]);
                refresh.run();
                return;
            }
            if (id >= 1000 && id < 1100) {
                StarLife.toggleTodo(id - 1000);
                refresh.run();
                return;
            }
            if (id >= 2000 && id < 2100) {
                org.json.JSONObject o = StarLife.later().optJSONObject(id - 2000);
                if (o != null) StarLife.openMessage(this, o.optLong("d"), o.optInt("m"));
                return;
            }
            if (id >= 230 && id < 230 + StarDecor.BG.length) {
                if (id - 230 >= 2 && !StarMod.requirePro(this, "Живой фон чата")) return;
                StarDecor.setBg(id - 230);
                refresh.run();
                return;
            }
            if (id >= 5000 && id < 5000 + SECTIONS.length) {
                presentFragment(new Screen(id - 5000));
                return;
            }
            if (id >= 3000 && id < 3100) {
                StarMod.openService(this, id - 3000);
                return;
            }
            if (id >= 180 && id < 180 + StarLife.GOALS.length) {
                StarLife.setGoal(StarLife.GOALS[id - 180]);
                refresh.run();
                return;
            }
            switch (id) {
                case 1: StarTrial.offer(this, refresh); return;
                case 2: presentFragment(new StarPremiumActivity()); return;
                case 3: org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=premium"); return;
                case 4: presentFragment(new StarPlusActivity()); return;
                case 10: StarMod.toggle("ghost_typing", false); StarGhost.arm(); break;
                case 11: StarMod.toggle("ghost_read", false); StarGhost.arm(); break;
                case 12: StarMod.toggle("ghost_online", false); StarGhost.arm(); break;
                case 13: StarMod.toggle("ghost_stories", false); StarGhost.arm(); break;
                case 400: StarGhost.choosePreset(this, refresh); return;
                case 401:
                    if (!StarGhost.scheduleOn() && !StarMod.requirePro(this, "Призрак по расписанию")) return;
                    StarGhost.toggle("ghost_sched", false);
                    break;
                case 402: StarGhost.chooseSchedule(this, refresh); return;
                case 403: StarGhost.chooseAutoOff(this, refresh); return;
                case 404: StarGhost.chooseChatMode(this, refresh); return;
                case 405: StarGhost.toggle("ghost_read_reply", true); break;
                case 406:
                    if (!raw("ghost_listen", false) && !StarMod.requirePro(this, "Без «прослушано»")) return;
                    StarGhost.toggle("ghost_listen", false);
                    break;
                case 407: StarGhost.toggle("ghost_warn", false); break;
                case 408: StarGhost.toggle("ghost_offline_after", true); break;
                case 409: StarGhost.toggle("ghost_button", true); break;
                case 410: StarVault.chooseNotify(this, refresh); return;
                case 411: StarVault.toggle("vault_channels", true); break;
                case 412: StarVault.toggle("vault_media", true); break;
                case 413: StarVault.chooseKeep(this, refresh); return;
                case 414: StarVault.togglePin(this, refresh); return;
                case 415: presentFragment(new StarMemory.Screen()); return;
                case 417: StarVault.toggle("vault_weekly", true); break;
                case 416:
                    choose("Двойной тап по сообщению", StarExtra.TAP_ACTIONS, w -> {
                        StarExtra.setTapAction(w);
                        refresh.run();
                    });
                    return;
                case 14: StarPrivacy.clearNoRead(); break;
                case 15: StarPlus.toggle("plus_incognito_kb", false); break;
                case 16: StarPlus.toggle("plus_secure_locked", true); break;
                case 20: StarLock.askPinThen(this, () -> presentFragment(new StarHiddenActivity())); return;
                case 21: StarLock.setupPin(this, refresh); return;
                case 22: StarPrivacy.setupDecoy(this, refresh); return;
                case 23: StarPrivacy.setupWord(this, refresh); return;
                case 24: StarPlus.setupPanic(this, refresh); return;
                case 25: StarPlus.toggle("plus_hide_recents", false); StarPlus.applyRecents(getParentActivity()); break;
                case 26:
                    if (!StarMod.requirePro(this, "Шторка приватности")) return;
                    StarMod.toggle("shade_on", false);
                    StarShade.update();
                    break;
                case 30: presentFragment(new StarLabelsActivity()); return;
                case 31: presentFragment(new StarNotesActivity()); return;
                case 32: presentFragment(new StarFocus.Screen()); return;
                case 33: presentFragment(new StarStarred.Screen()); return;
                case 34: presentFragment(new StarSearchActivity()); return;
                case 35: StarPlus.readAll(this); return;
                case 36: StarMod.toggle("filter_enabled", false); break;
                case 37: StarMod.editFilterWords(this, refresh); return;
                case 38: StarPlus.toggle("plus_censor", false); break;
                case 39: StarPlus.toggle("plus_profile_history", true); break;
                case 40: StarMod.toggle("save_deleted", true); break;
                case 41: StarMod.toggle("save_edits", true); break;
                case 42: StarVault.open(this); return;
                case 43: StarMod.toggle("show_seconds", false); break;
                case 44: StarPlus.toggle("plus_repeat", true); break;
                case 45: StarMedia.toggle("media_hide_share", false); break;
                case 55: StarMedia.toggle("media_silent", false); break;
                case 56: StarMedia.toggle("media_confirm_stickers", false); break;
                case 57: StarMedia.toggle("media_confirm_voice", false); break;
                case 58: StarSend.toggleTone(); break;
                case 59: StarSend.toggleVoiceText(); break;
                case 60: StarSend.toggleRemindDetect(); break;
                case 61: StarMore.manageTemplates(this, refresh); return;
                case 62: presentFragment(new StarBroadcastActivity()); return;
                case 70: StarPremium.toggle("prem_fast_dl", true); break;
                case 71: StarPremium.toggle("prem_save_stories", true); break;
                case 72: StarMedia.toggle("media_round_back", false); break;
                case 80: presentFragment(new StarAiChatActivity()); return;
                case 81: StarMore.dailySummary(this); return;
                case 82: StarAuto.toggleTranslate(); break;
                case 90: StarMod.toggle("plugins_on", true); break;
                case 91:
                    if (getParentActivity() != null) new AlertDialog.Builder(getParentActivity()).setTitle("🧩 Команды").setMessage(StarPlugins.HELP).setPositiveButton("OK", null).show();
                    return;
                case 92: StarPlugins.editMacros(this, refresh); return;
                case 100: presentFragment(new StarCosmetics.Shop()); return;
                case 101: presentFragment(new StarStyleShop()); return;
                case 102: presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)); return;
                case 103: presentFragment(new StarHubActivity()); return;
                case 110: presentFragment(new StarBizActivity()); return;
                case 111: StarPremium.toggle("prem_away", false); break;
                case 112: StarPremium.editText(this, true, refresh); return;
                case 113: StarPremium.toggle("prem_greet", false); break;
                case 114: StarPremium.editText(this, false, refresh); return;
                case 120:
                    if (getParentActivity() != null) new AlertDialog.Builder(getParentActivity()).setTitle("🎮 Игры в чате")
                            .setMessage("Открой чат с другом → ⋮ → «🎮 Игры»: крестики-нолики, правда или действие, угадай по эмодзи, кубик и дартс.")
                            .setPositiveButton("Понятно", null).show();
                    return;
                case 121: presentFragment(new StarFriends()); return;
                case 122: presentFragment(new StarAsk()); return;
                case 123: presentFragment(new StarAchieve.Screen()); return;
                case 124: presentFragment(new StarWrapped()); return;
                case 125: presentFragment(new StarCard()); return;
                case 126: presentFragment(new StarStudy()); return;
                case 127: StarVoice.choose(this); return;
                case 128: StarProfile.chooseStatus(this, refresh); return;
                case 130: presentFragment(new StarAdsActivity()); return;
                case 140: StarMod.toggle("chats_button", true); break;
                case 141: StarMore.backup(this); return;
                case 142: StarMod.showStats(this); return;
                case 143: StarMod.checkUpdate(this, true); return;
                case 144: StarMod.confirmClear(this); return;
                case 145: presentFragment(new StarHubActivity()); return;
                case 220: presentFragment(new StarThemeBuilder()); return;
                case 221: StarDecor.toggle("decor_rain", true); break;
                case 222: StarDecor.toggle("decor_stripe", true); NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload); break;
                case 223: StarDecor.toggle("decor_bubble", false); StarDecor.updateBubble(); break;
                case 224:
                    choose("📳 Вибро при отправке", StarDecor.HAPTICS, w -> { StarDecor.setHaptic(w); StarDecor.onSend(); refresh.run(); });
                    return;
                case 225: {
                    String[] names = new String[StarCosmetics.SOUNDS.length + 1];
                    names[0] = "Как в Telegram";
                    for (int i = 0; i < StarCosmetics.SOUNDS.length; i++) names[i + 1] = (String) StarCosmetics.SOUNDS[i][0];
                    choose("🔊 Звук отправки (после перезапуска)", names, w -> { StarDecor.setSendSound(w - 1); refresh.run(); });
                    return;
                }
                case 226: StarDecor.setMinimal(!StarDecor.minimal()); break;
                case 227: StarDecor.toggle("decor_music", true); break;
                case 228:
                    if (!StarMod.requirePro(this, "Цвет имени")) return;
                    choose("🖍 Цвет имени для друзей", StarDecor.NAME_COLOR_TITLES, w -> { StarDecor.setLook(this, "look_name_color", StarDecor.NAME_COLORS[w]); refresh.run(); });
                    return;
                case 229: {
                    if (!StarMod.requirePro(this, "Шрифт имени")) return;
                    String[] st = new String[StarStyle.STYLES.length + 1];
                    st[0] = "Обычный";
                    for (int i = 0; i < StarStyle.STYLES.length; i++) st[i + 1] = StarStyle.STYLES[i];
                    choose("✨ Шрифт имени для друзей", st, w -> { StarDecor.setLook(this, "look_name_style", w - 1); refresh.run(); });
                    return;
                }
                case 240:
                    choose("🖼 Обложка витрины", StarDecor.COVERS, w -> { StarDecor.setLook(this, "look_cover", w); refresh.run(); });
                    return;
                case 241: StarDecor.chooseBirthday(this, refresh); return;
                case 210: StarStyle.chooseAuto(this, refresh); return;
                case 211: StarStyle.fancyName(this); return;
                case 212: StarStyle.aiBio(this); return;
                case 213: StarProfile.editShowcase(this, refresh); return;
                case 150: StarFun.pet(this, refresh); return;
                case 151: StarFun.horoscope(this); return;
                case 152: StarFun.spin(this, refresh); return;
                case 160: presentFragment(new StarFeedActivity()); return;
                case 161: if (StarMod.requirePro(this, "Фильтр постов каналов")) StarGuard.editChannelWords(this, refresh); return;
                case 171: StarLife.clearDone(); break;
                case 172:
                    if (getParentActivity() != null) new AlertDialog.Builder(getParentActivity()).setTitle("⏱ Экранное время").setMessage(StarLife.weekReport()).setPositiveButton("OK", null).show();
                    return;
                case 190: StarGuard.showDevices(this); return;
                case 191: StarMod.toggle("guard_logins", true); break;
                case 192:
                    if (!StarMod.requirePro(this, "Аварийный жест")) return;
                    StarMod.toggle("guard_shake", false);
                    StarGuard.update();
                    break;
                case 193: StarMod.toggle("scam_guard", true); break;
                case 300: StarPro.autoFolders(this, refresh); return;
                case 301: StarPro.toggle("pro_kb_scroll", false); break;
                case 302: StarPro.toggle("pro_no_next", false); break;
                case 303: StarPro.toggle("pro_confirm_call", false); break;
                case 304: StarPro.toggle("pro_details", true); break;
                case 305: StarPro.toggle("pro_battery", true); break;
                case 306: StarX.toggle("x_quiet", false); break;
                case 307: StarX.chooseQuiet(this, refresh); return;
                case 308:
                    if (!StarX.keywordsOn() && StarX.keywordsCount() == 0) {
                        StarX.editKeywords(this, refresh);
                        return;
                    }
                    StarX.toggle("x_kw", false);
                    break;
                case 309: StarX.editKeywords(this, refresh); return;
                case 310: StarX.toggle("x_clean_links", true); break;
                case 311: StarX.toggle("x_streamer", false); break;
                case 312: StarX.chooseSleep(this, refresh); return;
                case 200: StarFun.balance(this); return;
                case 201: StarFun.donate(this); return;
                default: return;
            }
            refresh.run();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            if (item.id >= 1000 && item.id < 1100) {
                StarLife.removeAt("life_todos", item.id - 1000);
            } else if (item.id >= 2000 && item.id < 2100) {
                StarLife.removeAt("life_later", item.id - 2000);
            } else {
                return false;
            }
            if (listView != null) listView.adapter.update(true);
            return true;
        }
    }
}
