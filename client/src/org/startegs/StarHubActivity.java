/*
 * StarTegs — экран «StarTegs»: сервисы экосистемы, фирменное оформление и настройки,
 * которые пользователь меняет сам. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

public class StarHubActivity extends UniversalFragment {

    private static final int ID_SERVICE = 100;        // 100..119 — сервисы
    private static final int ID_PALETTE_OFF = 210;     // 210 — выключено, 211.. — палитры
    private static final int ID_UPDATE = 700;
    private static final int ID_CHECK_UPDATE = 701;
    private static final int ID_GHOST_ONLINE = 402;
    private static final int ID_GHOST_STORIES = 403;
    private static final int ID_AI_CHAT = 1200;
    private static final int ID_NOTES = 1300;
    private static final int ID_CUSTOM_PALETTE = 1400;
    private static final int ID_SEASON = 1500;        // 1500.. — сезонные эффекты
    private static final int ID_TAP = 1600;           // 1600.. — двойное нажатие
    private static final int ID_SCAM = 1700;
    private static final int ID_BIOMETRIC = 1701;
    private static final int ID_LABELS = 1702;
    private static final int ID_TEMPLATES = 1900;
    private static final int ID_DAILY = 1901;
    private static final int ID_BACKUP = 1902;
    private static final int ID_BROADCAST = 1903;
    private static final int ID_DECOY = 2000;
    private static final int ID_WORD = 2001;
    private static final int ID_WORD_ONLY = 2002;
    private static final int ID_NOREAD_CLEAR = 2003;
    private static final int ID_UNDO = 2010;          // 2010.. — секунды отмены
    private static final int ID_TONE = 2020;
    private static final int ID_VOICE_TEXT = 2021;
    private static final int ID_REMIND_DETECT = 2022;
    private static final int ID_FOCUS = 2030;
    private static final int ID_STARRED = 2031;
    private static final int ID_WRAPPED = 2032;
    private static final int ID_CARD = 2033;
    private static final int ID_WALL = 2034;
    private static final int ID_WALL_SEASON = 2035;
    private static final int[] UNDO_SECONDS = {0, 3, 5, 10};
    private static final int ID_GLASS = 2100;
    private static final int ID_SHOP = 2200;
    private static final int ID_FRIENDS = 2201;
    private static final int ID_ASK = 2202;
    private static final int ID_GAMES = 2203;
    private static final int ID_VOICE = 2204;
    private static final int ID_STUDY = 2205;
    private static final int ID_STICKER = 2206;
    private static final int ID_GRADIENT = 2101;
    private static final int ID_NEON = 2102;
    private static final int ID_FONT = 2103;
    private static final int ID_ANIM = 2104;
    private static final int ID_AVATAR_THEME = 2105;
    private static final int ID_COMPACT = 2106;
    private static final int ID_FOLDERS = 2107;
    private static final int ID_SEASON_ICON = 2108;
    private static final int ID_AR_PROMPT = 1800;
    private static final int ID_AR_MARK = 1801;
    private static final int ID_AR_OFF = 1802;
    private static final int ID_AI_TRANSLATE = 1803;
    private static final int ID_TIER = 1100;
    private static final int ID_TIER_BUY = 1101;
    private static final int ID_HIDDEN = 1102;
    private static final int ID_ARCHIVE = 1103;
    private static final int ID_ARCHIVE_NOW = 1104;
    private static final int ID_DELETED_LIST = 302;
    private static final int ID_FILTER = 800;
    private static final int ID_FILTER_WORDS = 801;
    private static final int ID_FILTER_USERS_CLEAR = 802;
    private static final int ID_LOCK_PIN = 900;
    private static final int ID_LOCK_CLEAR = 901;
    private static final int ID_CHATS_BUTTON = 502;
    private static final int ID_THREE_LINES = 503;
    private static final int ID_APP_ICON = 504;
    private static final int ID_SHAPE = 510;          // 510..512 — форма аватарок
    private static final int ID_TG_THEMES = 201;
    private static final int ID_SECONDS = 202;
    private static final int ID_SAVE_DELETED = 300;
    private static final int ID_SAVE_EDITS = 301;
    private static final int ID_GHOST_TYPING = 400;
    private static final int ID_GHOST_READ = 401;
    private static final int ID_HIDE_STORIES = 500;
    private static final int ID_HIDE_PROMO = 501;
    private static final int ID_STATS = 600;
    private static final int ID_CLEAR = 601;

    private static String pro(String title) {
        return StarMod.isPro() ? title : title + "  🔒";
    }

    @Override
    public void onResume() {
        super.onResume();
        Runnable refresh = () -> {
            if (listView != null) listView.adapter.update(true);
        };
        StarCloud.refreshTier(this, false, refresh);
        StarCloud.syncArchive(this, false, refresh);
    }

    @Override
    protected CharSequence getTitle() {
        return "⭐ StarTegs";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (getParentActivity() != null) {
            items.add(UItem.asCustom(StarHero.create(getParentActivity(), currentAccount, which -> {
                switch (which) {
                    case 0: presentFragment(new StarAiChatActivity()); break;
                    case 1: presentFragment(new StarDeletedActivity()); break;
                    case 2: StarLock.askPinThen(this, () -> presentFragment(new StarHiddenActivity())); break;
                    case 3: presentFragment(new StarLabelsActivity()); break;
                    default: presentFragment(new StarNotesActivity());
                }
            })));
        }
        items.add(UItem.asHeader("🔥 Для друзей"));
        items.add(UItem.asButton(ID_SHOP, R.drawable.msg_premium_badge, "🛍 Магазин: рамки, эффекты, темы, звуки").accent());
        items.add(UItem.asButton(ID_FRIENDS, R.drawable.msg_contacts, "💞 Лучшие друзья и стрики 🔥"));
        items.add(UItem.asButton(ID_ASK, R.drawable.msg_link, "🤫 Анонимные вопросы"));
        items.add(UItem.asButton(ID_GAMES, R.drawable.msg_fave, "🎮 Игры в чате"));
        items.add(UItem.asButton(ID_VOICE, R.drawable.msg_voice_unmuted, "🎙 Голос в голосовых", StarVoice.title()));
        items.add(UItem.asButton(ID_STUDY, R.drawable.msg_calendar2, "📚 Учёба: расписание, домашка, репетитор"));
        items.add(UItem.asButton(ID_STICKER, R.drawable.msg_photos, "🖼 Стикер из фото"));
        items.add(UItem.asShadow("Рамки и эффекты видят друзья, у которых тоже StarTegs — зови их 😉 Покупки — за 💎 кристаллы в @"
                + StarModConfig.ECO_BOT + ", Premium открывает всё."));
        if (StarMod.updateAvailable()) {
            items.add(UItem.asButton(ID_UPDATE, R.drawable.msg_download, "🔔 Скачать новую версию", StarMod.updateName()).accent());
            items.add(UItem.asShadow(null));
        }
        if (!StarModConfig.ECO_BOT.isEmpty()) {
            items.add(UItem.asHeader("Тариф"));
            items.add(UItem.asButton(ID_TIER, R.drawable.msg_premium_badge, "Ваш тариф: " + StarMod.tierTitle(), "обновить"));
            if (!StarMod.isPro()) {
                items.add(UItem.asButton(ID_TIER_BUY, R.drawable.msg_fave, "Оформить Premium за ⭐ Stars").accent());
            }
            items.add(UItem.asShadow("Premium и VIP оформляются в @" + StarModConfig.ECO_BOT + " и открывают в моде приватность, замок и скрытые чаты, все палитры и иконки. Отмечены 🔒."));
        }
        if (StarAi.enabled()) {
            items.add(UItem.asHeader("Нейросеть"));
            items.add(UItem.asButton(ID_AI_CHAT, R.drawable.msg_bot, "🤖 Чат с ИИ"));
            items.add(UItem.asButton(ID_DAILY, R.drawable.msg_calendar2, "☀️ Сводка дня"));
            items.add(UItem.asCheck(ID_AI_TRANSLATE, "🌍 Перевод чатов через ИИ").setChecked(StarAuto.aiTranslate()));
            items.add(UItem.asButton(ID_AR_PROMPT, R.drawable.msg_edit, "🤖 Автоответ: как отвечать", StarAuto.count() > 0 ? "вкл. в " + StarAuto.count() : ""));
            items.add(UItem.asCheck(ID_AR_MARK, "Помечать автоответы 🤖").setChecked(StarAuto.markReplies()));
            if (StarAuto.count() > 0) items.add(UItem.asButton(ID_AR_OFF, R.drawable.msg_delete, "Выключить автоответ везде").red());
            items.add(UItem.asShadow("Перевод: кнопка «Перевести» над иностранным чатом работает без Telegram Premium — переводит нейросеть. "
                    + "Автоответ: профиль собеседника → ⋮ → «Автоответ ИИ». Отвечает, пока приложение работает, не чаще раза в 20 с и 20 раз в час; если вы ответили сами — молчит 15 минут."));
            items.add(UItem.asShadow("Также: долгое нажатие на сообщение → «Спросить ИИ», меню чата ⋮ → «Что я пропустил?» и «Улучшить мой текст». Лимиты и Premium — из @" + StarModConfig.AI_BOT + "."));
        }
        items.add(UItem.asHeader("✨ Фишки"));
        items.add(UItem.asButton(ID_FOCUS, R.drawable.msg_fave, "🎯 Режим «Фокус»", StarFocus.active() ? StarFocus.statusText() : ""));
        items.add(UItem.asButton(ID_STARRED, R.drawable.msg_fave, "⭐ Избранные сообщения", StarStarred.count() > 0 ? String.valueOf(StarStarred.count()) : ""));
        items.add(UItem.asButton(ID_WRAPPED, R.drawable.msg_stats, "🎁 Итоги года"));
        items.add(UItem.asButton(ID_CARD, R.drawable.msg_qrcode, "🪪 Визитка с QR-кодом"));
        items.add(UItem.asCheck(ID_WALL, "🌄 Живые обои в чатах").setChecked(StarWall.enabled()));
        if (StarWall.enabled()) items.add(UItem.asCheck(ID_WALL_SEASON, "Сезонный эффект и в чатах").setChecked(StarWall.seasonInChats()));
        items.add(UItem.asShadow("Живые обои: фон чатов мягко меняется по времени суток — утро, день, вечер, ночь (сейчас " + StarWall.phaseName() + "). "
                + "Применяется к вновь открытым чатам. Избранные: долгое нажатие на сообщение → «В избранные ⭐»."));

        items.add(UItem.asHeader("Отправка"));
        for (int i = 0; i < UNDO_SECONDS.length; i++) {
            int sec = UNDO_SECONDS[i];
            items.add(UItem.asRadio(ID_UNDO + i, sec == 0 ? "Отмена отправки: выключена" : "Отмена отправки: " + sec + " с").setChecked(StarSend.undoSeconds() == sec));
        }
        if (StarAi.enabled()) {
            items.add(UItem.asCheck(ID_TONE, "😬 Проверять тон перед отправкой (ИИ)").setChecked(StarSend.toneCheck()));
            items.add(UItem.asCheck(ID_VOICE_TEXT, "🎙 Предлагать отправить голосовое текстом").setChecked(StarSend.voiceToText()));
        }
        items.add(UItem.asCheck(ID_REMIND_DETECT, "⏰ Предлагать напоминание из переписки").setChecked(StarSend.remindDetect()));
        items.add(UItem.asShadow("Отмена отправки: текст уходит через несколько секунд, а внизу кнопка «Отменить» — текст вернётся в поле ввода. "
                + "Напоминания: если в открытом чате пишут «давай завтра в 7», появится кнопка «Напомнить»."));

        items.add(UItem.asHeader("Сервисы"));
        boolean any = false;
        for (int i = 0; i < StarModConfig.SERVICES.length; i++) {
            String[] s = StarModConfig.SERVICES[i];
            if (s[2].isEmpty()) continue;
            any = true;
            items.add(SettingsActivity.SettingCell.Factory.of(ID_SERVICE + i, StarMod.color(s[3]), StarMod.color(s[4]),
                    StarMod.iconFor(s[5]), s[0], s[1]));
        }
        items.add(UItem.asShadow(any ? "Откроется бот, а в нём — его мини-приложение." : "Сервисы не настроены в startegs.properties."));

        items.add(UItem.asHeader("Фирменный дизайн"));
        int active = StarTheme.activePalette();
        items.add(UItem.asRadio(ID_PALETTE_OFF, "Выключен (обычная тема)").setChecked(active < 0));
        for (int i = 0; i < StarTheme.PALETTES.length; i++) {
            items.add(UItem.asRadio(ID_PALETTE_OFF + 1 + i, "StarTegs · " + (i == 0 ? StarTheme.PALETTES[i][1] : pro(StarTheme.PALETTES[i][1]))).setChecked(active == i));
        }
        items.add(UItem.asRadio(ID_CUSTOM_PALETTE, pro("🎨 Своя палитра…")).setChecked(active == -2));
        items.add(UItem.asShadow("У каждой палитры есть светлый и тёмный вариант — они переключаются вместе с ночным режимом Telegram. При выключении вернётся прежняя тема."));
        items.add(UItem.asHeader("Сезонные эффекты"));
        for (int i = 0; i < StarSeason.MODES.length; i++) {
            items.add(UItem.asRadio(ID_SEASON + i, StarSeason.MODES[i]).setChecked(StarSeason.mode() == i));
        }
        items.add(UItem.asShadow("Падают поверх списка чатов и не мешают нажатиям. «Авто»: зимой снег, весной лепестки, летом светлячки, осенью листья."));
        items.add(UItem.asHeader("Двойное нажатие на сообщение"));
        for (int i = 0; i < StarExtra.TAP_ACTIONS.length; i++) {
            items.add(UItem.asRadio(ID_TAP + i, StarExtra.TAP_ACTIONS[i]).setChecked(StarExtra.tapAction() == i));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Настройки вида"));
        items.add(UItem.asCheck(ID_SECONDS, "Секунды во времени сообщений").setChecked(StarMod.showSeconds()));
        items.add(UItem.asButton(ID_TG_THEMES, R.drawable.msg_theme, "Темы, шрифт и пузыри сообщений"));
        items.add(UItem.asShadow("В «Темах» можно собрать свою: цвета, обои, размер шрифта, скругление сообщений."));

        items.add(UItem.asHeader("Дизайн"));
        items.add(UItem.asCheck(ID_GRADIENT, "🌈 Градиентные пузыри").setChecked(StarLook.gradientBubbles()));
        items.add(UItem.asCheck(ID_NEON, "🌃 Неоновый OLED-режим").setChecked(StarLook.neon()));
        items.add(UItem.asButton(ID_AVATAR_THEME, R.drawable.msg_photos, "🖼 Тема по моей аватарке"));
        items.add(UItem.asCheck(ID_GLASS, "🫧 Стеклянные панели (размытие)").setChecked(StarLook.glass()));
        items.add(UItem.asButton(ID_FONT, R.drawable.msg_edit, "🔤 Шрифт", StarLook.FONTS[StarLook.font()][1]));
        items.add(UItem.asCheck(ID_ANIM, "✨ Анимации сообщений").setChecked(StarLook.animations()));
        items.add(UItem.asCheck(ID_COMPACT, "📏 Компактный режим").setChecked(StarLook.compact()));
        items.add(UItem.asCheck(ID_FOLDERS, "🗂 Цветные папки").setChecked(StarLook.folderColors()));
        items.add(UItem.asCheck(ID_SEASON_ICON, "🍂 Иконка по сезону").setChecked(StarLook.seasonIcon()));
        items.add(UItem.asShadow("Градиент и неон работают с темами StarTegs (включатся сами); неон — для тёмной темы, фон чёрный, акценты светятся. "
                + "Анимации: новые сообщения появляются с отскоком, отправка — вспышка в цвет палитры. Компактный: мельче текст и плотнее пузыри. "
                + "Иконка по сезону сейчас — " + StarLook.seasonIconName() + ". Шрифт меняется в чатах и списке чатов; полностью — после перезапуска."));

        items.add(UItem.asHeader("Сообщения"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые сообщения").setChecked(StarMod.saveDeleted()));
        items.add(UItem.asCheck(ID_SAVE_EDITS, "Сохранять историю изменений").setChecked(StarMod.saveEdits()));
        items.add(UItem.asCheck(ID_ARCHIVE, "Копировать удалённые в облачный архив бота").setChecked(StarCloud.archiveEnabled()));
        if (StarCloud.archiveEnabled()) {
            items.add(UItem.asButton(ID_ARCHIVE_NOW, R.drawable.msg_download, "Отправить в архив сейчас"));
        }
        items.add(UItem.asButton(ID_DELETED_LIST, R.drawable.msg_delete, "Все удалённые сообщения"));
        items.add(UItem.asButton(ID_LABELS, R.drawable.msg_folders, "🏷 Метки чатов"));
        items.add(UItem.asButton(ID_TEMPLATES, R.drawable.msg_edit, "💬 Шаблоны ответов", StarMore.templatesCount() == 0 ? "" : String.valueOf(StarMore.templatesCount())));
        items.add(UItem.asButton(ID_BROADCAST, R.drawable.msg_send, "📣 Рассылка по чатам"));
        items.add(UItem.asButton(ID_BACKUP, R.drawable.msg_download, "💾 Резервная копия настроек"));
        items.add(UItem.asButton(ID_NOTES, R.drawable.msg_edit, "📝 Мои заметки", StarTools.allNotes().isEmpty() ? "" : String.valueOf(StarTools.allNotes().size())));
        items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑 удалено», старые версии правок — в меню сообщения. Облачный архив отправляет удалённые в ваш бот StarTegs, где их можно искать."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asCheck(ID_GHOST_TYPING, pro("Не показывать, что я печатаю")).setChecked(StarMod.ghostTyping()));
        items.add(UItem.asCheck(ID_GHOST_READ, pro("Не отправлять «прочитано» в личных чатах")).setChecked(StarMod.ghostRead()));
        items.add(UItem.asCheck(ID_GHOST_ONLINE, pro("Не показывать «в сети»")).setChecked(StarMod.ghostOnline()));
        items.add(UItem.asCheck(ID_GHOST_STORIES, pro("Смотреть истории тайно")).setChecked(StarMod.ghostStories()));
        items.add(UItem.asShadow("Собеседник не увидит «печатает…», две галочки и вас в сети, а автор истории — вас среди просмотревших. "
                + "Чаты без «прочитано» останутся непрочитанными на других ваших устройствах."));

        items.add(UItem.asHeader("Безопасность"));
        items.add(UItem.asCheck(ID_SCAM, "🛡 Предупреждать о мошенниках").setChecked(StarExtra.guardEnabled()));
        if (StarLock.biometricAvailable()) {
            items.add(UItem.asCheck(ID_BIOMETRIC, "👆 Открывать замок по отпечатку").setChecked(StarLock.biometricEnabled()));
        }
        items.add(UItem.asShadow("Подозрительные сообщения от незнакомых (просьбы прислать код, «служба поддержки Telegram», «вы выиграли», поддельные ссылки) помечаются ⚠️. Контакты не проверяются."));
        items.add(UItem.asHeader("Фильтр"));
        items.add(UItem.asCheck(ID_FILTER, "Скрывать сообщения по фильтру").setChecked(StarMod.filterEnabled()));
        int words = StarMod.filterWordsText().isEmpty() ? 0 : StarMod.filterWordsText().split("[\\n,]").length;
        items.add(UItem.asButton(ID_FILTER_WORDS, R.drawable.msg_edit, "Слова для фильтра", words > 0 ? String.valueOf(words) : "нет"));
        if (StarMod.filteredUsersCount() > 0) {
            items.add(UItem.asButton(ID_FILTER_USERS_CLEAR, R.drawable.msg_delete, "Снова показывать скрытых людей", String.valueOf(StarMod.filteredUsersCount())));
        }
        items.add(UItem.asShadow("Текст подходящих сообщений заменяется пометкой «скрыто фильтром». Скрыть все сообщения человека — профиль → ⋮ → «Скрывать его сообщения»."));

        items.add(UItem.asHeader(StarMod.isPro() ? "Закрытые и скрытые чаты" : "Закрытые и скрытые чаты  🔒"));
        if (!StarPrivacy.hideEntry()) {
            items.add(UItem.asButton(ID_HIDDEN, R.drawable.msg_permissions, "🙈 Скрытые чаты", StarHidden.count() > 0 ? String.valueOf(StarHidden.count()) : ""));
        }
        items.add(UItem.asButton(ID_LOCK_PIN, R.drawable.msg_permissions, StarLock.hasPin() ? "Сменить PIN-код" : "Задать PIN-код"));
        if (StarLock.lockedCount() > 0) {
            items.add(UItem.asButton(ID_LOCK_CLEAR, R.drawable.msg_delete, "Снять все замки", String.valueOf(StarLock.lockedCount())));
        }
        items.add(UItem.asShadow("Закрыть или скрыть чат: профиль чата → ⋮ → «Закрыть чат PIN-кодом» или «Скрыть чат из списка». Скрытые открываются здесь по PIN."));

        items.add(UItem.asHeader("Маскировка"));
        items.add(UItem.asButton(ID_DECOY, R.drawable.msg_permissions, "🎭 Фальшивый код-пароль", StarPrivacy.hasDecoy() ? "вкл" : ""));
        items.add(UItem.asButton(ID_WORD, R.drawable.msg_permissions, "🔎 Секретное слово в поиске", StarPrivacy.hasSecretWord() ? "вкл" : ""));
        if (StarPrivacy.hasSecretWord()) {
            items.add(UItem.asCheck(ID_WORD_ONLY, "Убрать «Скрытые чаты» с этого экрана").setChecked(StarPrivacy.hideEntry()));
        }
        if (StarPrivacy.noReadCount() > 0) {
            items.add(UItem.asButton(ID_NOREAD_CLEAR, R.drawable.msg_delete, "Снова отправлять «прочитано» всем", String.valueOf(StarPrivacy.noReadCount())));
        }
        items.add(UItem.asShadow("Фальшивый код: введите его вместо настоящего кода-пароля Telegram — приложение откроется без скрытых и закрытых чатов и без StarTegs. "
                + "Секретное слово: наберите его в поиске над чатами — откроются скрытые чаты. "
                + "«Прочитано» выборочно: профиль чата → ⋮ → «Не отправлять «прочитано» этому чату»."));

        items.add(UItem.asHeader("Интерфейс"));
        items.add(UItem.asCheck(ID_CHATS_BUTTON, "Кнопка ⭐ StarTegs над списком чатов").setChecked(StarMod.showChatsButton()));
        items.add(UItem.asCheck(ID_THREE_LINES, "Подробный список чатов (3 строки)").setChecked(SharedConfig.useThreeLinesLayout));
        items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории над списком чатов").setChecked(StarMod.hideStories()));
        items.add(UItem.asCheck(ID_HIDE_PROMO, "Скрыть Premium, Business и подарки в настройках").setChecked(StarMod.hidePromo()));
        items.add(UItem.asButton(ID_APP_ICON, R.drawable.msg_theme, "Иконка приложения"));
        items.add(UItem.asShadow("Иконки StarTegs выбираются в разделе «Темы» → «Иконка приложения»."));

        items.add(UItem.asHeader("Форма аватарок"));
        String[] shapes = {"Круг", "Скруглённый квадрат", "Квадрат"};
        for (int i = 0; i < shapes.length; i++) {
            items.add(UItem.asRadio(ID_SHAPE + i, shapes[i]).setChecked(StarMod.avatarShape() == i));
        }
        items.add(UItem.asShadow("Меняет аватарки в списке чатов и в группах. Новые строки сразу, остальные — после перезапуска приложения."));

        items.add(UItem.asButton(ID_STATS, R.drawable.msg_stats, "Что сохранил мод"));
        items.add(UItem.asButton(ID_CHECK_UPDATE, R.drawable.msg_download, "Проверить обновления"));
        items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, "Очистить сохранённое модом").red());
        items.add(UItem.asShadow("StarTegs " + StarModConfig.MOD_VERSION + " — неофициальный клиент на основе исходного кода Telegram. "
                + "Всё, что сохраняет мод, хранится только на этом телефоне."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_SERVICE && id < ID_SERVICE + 20) {
            StarMod.openService(this, id - ID_SERVICE);
            return;
        }
        if (id >= ID_PALETTE_OFF && id <= ID_PALETTE_OFF + StarTheme.PALETTES.length) {
            StarTheme.setPalette(this, id - ID_PALETTE_OFF - 1);
            if (listView != null) listView.adapter.update(true);
            return;
        }
        if (id == ID_CUSTOM_PALETTE) {
            StarTheme.chooseCustom(this);
            return;
        }
        if (id >= ID_SEASON && id < ID_SEASON + StarSeason.MODES.length) {
            StarSeason.setMode(id - ID_SEASON);
            if (listView != null) listView.adapter.update(true);
            return;
        }
        if (id >= ID_TAP && id < ID_TAP + StarExtra.TAP_ACTIONS.length) {
            StarExtra.setTapAction(id - ID_TAP);
            if (listView != null) listView.adapter.update(true);
            return;
        }
        if (id >= ID_SHAPE && id < ID_SHAPE + 3) {
            StarMod.setAvatarShape(id - ID_SHAPE);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload, true);
            if (listView != null) listView.adapter.update(true);
            return;
        }
        Runnable refresh = () -> {
            if (listView != null) listView.adapter.update(true);
        };
        if ((id == ID_GHOST_TYPING || id == ID_GHOST_READ || id == ID_GHOST_ONLINE || id == ID_GHOST_STORIES) && !StarMod.requirePro(this, "Приватность")) {
            return;
        }
        if (id >= ID_UNDO && id < ID_UNDO + UNDO_SECONDS.length) {
            StarSend.setUndoSeconds(UNDO_SECONDS[id - ID_UNDO]);
            refresh.run();
            return;
        }
        switch (id) {
            case ID_SHOP:
                presentFragment(new StarCosmetics.Shop());
                return;
            case ID_FRIENDS:
                presentFragment(new StarFriends());
                return;
            case ID_ASK:
                presentFragment(new StarAsk());
                return;
            case ID_GAMES:
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("🎮 Игры в чате")
                        .setMessage("Открой чат с другом → ⋮ → «🎮 Игры»: крестики-нолики, правда или действие, угадай по эмодзи, кубик и дартс.\n\n"
                                + "Чтобы сделать ход — долгое нажатие на сообщение с игрой → «Сделать ход». Друг без StarTegs видит поле, но ходить сможет только с модом 😉")
                        .setPositiveButton("Понятно", null)
                        .show();
                return;
            case ID_VOICE:
                StarVoice.choose(this);
                return;
            case ID_STUDY:
                presentFragment(new StarStudy());
                return;
            case ID_STICKER:
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("🖼 Стикер из фото")
                        .setMessage("Это уже умеет сам Telegram — фон вырезается автоматически:\n\n"
                                + "1. В любом чате открой панель стикеров.\n2. Нажми ➕ «Создать стикер».\n3. Выбери фото — человек или предмет вырежется сам.\n"
                                + "4. Добавь эмодзи и сохрани в свой набор.\n\nСтикер можно сразу отправить другу или собрать целый пак.")
                        .setPositiveButton("Понятно", null)
                        .show();
                return;
            case ID_GRADIENT:
                if (StarTheme.activePalette() > 0 && !StarMod.requirePro(this, "Палитры StarTegs")) return;
                StarLook.toggleThemeFlag(this, "look_gradient");
                break;
            case ID_NEON:
                StarLook.toggleThemeFlag(this, "look_neon");
                break;
            case ID_AVATAR_THEME:
                StarLook.themeFromAvatar(this);
                break;
            case ID_GLASS:
                StarLook.toggleGlass(this);
                break;
            case ID_FONT: {
                CharSequence[] names = new CharSequence[StarLook.FONTS.length];
                for (int i = 0; i < names.length; i++) names[i] = (i == StarLook.font() ? "✓ " : "") + StarLook.FONTS[i][1];
                new org.telegram.ui.ActionBar.AlertDialog.Builder(getParentActivity())
                        .setTitle("🔤 Шрифт")
                        .setItems(names, (d, which) -> {
                            StarLook.setFont(which);
                            refresh.run();
                        })
                        .show();
                return;
            }
            case ID_ANIM:
                StarMod.toggle("look_anim", true);
                break;
            case ID_COMPACT:
                StarLook.toggleCompact();
                break;
            case ID_FOLDERS:
                StarMod.toggle("look_folders", false);
                org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
                break;
            case ID_SEASON_ICON:
                StarLook.toggleSeasonIcon();
                break;
            case ID_DECOY:
                StarPrivacy.setupDecoy(this, refresh);
                return;
            case ID_WORD:
                StarPrivacy.setupWord(this, refresh);
                return;
            case ID_WORD_ONLY:
                StarPrivacy.toggleHideEntry();
                break;
            case ID_NOREAD_CLEAR:
                StarPrivacy.clearNoRead();
                break;
            case ID_TONE:
                StarSend.toggleTone();
                break;
            case ID_VOICE_TEXT:
                StarSend.toggleVoiceText();
                break;
            case ID_REMIND_DETECT:
                StarSend.toggleRemindDetect();
                break;
            case ID_FOCUS:
                presentFragment(new StarFocus.Screen());
                return;
            case ID_STARRED:
                presentFragment(new StarStarred.Screen());
                return;
            case ID_WRAPPED:
                presentFragment(new StarWrapped());
                return;
            case ID_CARD:
                presentFragment(new StarCard());
                return;
            case ID_WALL:
                StarWall.toggle();
                break;
            case ID_WALL_SEASON:
                StarWall.toggleSeason();
                break;
            case ID_BROADCAST:
                presentFragment(new StarBroadcastActivity());
                return;
            case ID_TEMPLATES:
                StarMore.manageTemplates(this, refresh);
                return;
            case ID_DAILY:
                StarMore.dailySummary(this);
                return;
            case ID_BACKUP:
                StarMore.backup(this);
                return;
            case ID_NOTES:
                presentFragment(new StarNotesActivity());
                return;
            case ID_AI_TRANSLATE:
                StarAuto.toggleTranslate();
                break;
            case ID_AR_PROMPT:
                StarAuto.editInstructions(this, refresh);
                return;
            case ID_AR_MARK:
                StarAuto.toggleMark();
                break;
            case ID_AR_OFF:
                StarAuto.disableAll();
                break;
            case ID_SCAM:
                StarMod.toggle("scam_guard", true);
                break;
            case ID_BIOMETRIC:
                StarLock.setBiometric(!StarLock.biometricEnabled());
                break;
            case ID_LABELS:
                presentFragment(new StarLabelsActivity());
                return;
            case ID_AI_CHAT:
                presentFragment(new StarAiChatActivity());
                return;
            case ID_TIER:
                StarCloud.refreshTier(this, true, refresh);
                return;
            case ID_TIER_BUY:
                Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=premium");
                return;
            case ID_HIDDEN:
                StarLock.askPinThen(this, () -> presentFragment(new StarHiddenActivity()));
                return;
            case ID_ARCHIVE:
                StarMod.toggle("cloud_archive", false);
                if (StarCloud.archiveEnabled()) StarCloud.syncArchive(this, true, refresh);
                break;
            case ID_ARCHIVE_NOW:
                StarCloud.syncArchive(this, true, refresh);
                return;
            case ID_DELETED_LIST:
                presentFragment(new StarDeletedActivity());
                return;
            case ID_FILTER:
                StarMod.toggle("filter_enabled", false);
                break;
            case ID_FILTER_WORDS:
                StarMod.editFilterWords(this, refresh);
                return;
            case ID_FILTER_USERS_CLEAR:
                StarMod.clearUserFilter();
                break;
            case ID_LOCK_PIN:
                StarLock.setupPin(this, refresh);
                return;
            case ID_LOCK_CLEAR:
                StarLock.unlockAll(this, refresh);
                return;
            case ID_CHATS_BUTTON:
                StarMod.toggle("chats_button", true);
                break;
            case ID_THREE_LINES:
                SharedConfig.setUseThreeLinesLayout(!SharedConfig.useThreeLinesLayout);
                break;
            case ID_APP_ICON:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                return;
            case ID_UPDATE:
                StarMod.offerUpdate(this);
                return;
            case ID_CHECK_UPDATE:
                StarMod.checkUpdate(this, true);
                return;
            case ID_GHOST_ONLINE:
                StarMod.toggle("ghost_online", false);
                break;
            case ID_GHOST_STORIES:
                StarMod.toggle("ghost_stories", false);
                break;
            case ID_TG_THEMES:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                return;
            case ID_SECONDS:
                StarMod.toggle("show_seconds", false);
                break;
            case ID_SAVE_DELETED:
                StarMod.toggle("save_deleted", true);
                break;
            case ID_SAVE_EDITS:
                StarMod.toggle("save_edits", true);
                break;
            case ID_GHOST_TYPING:
                StarMod.toggle("ghost_typing", false);
                break;
            case ID_GHOST_READ:
                StarMod.toggle("ghost_read", false);
                break;
            case ID_HIDE_STORIES:
                StarMod.toggle("hide_stories", false);
                break;
            case ID_HIDE_PROMO:
                StarMod.toggle("hide_promo", false);
                break;
            case ID_STATS:
                StarMod.showStats(this);
                return;
            case ID_CLEAR:
                StarMod.confirmClear(this);
                return;
            default:
                return;
        }
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
