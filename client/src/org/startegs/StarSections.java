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
            {"👻", "Приватность", "призрак, «прочитано», клавиатура"},
            {"🕳", "Двойное дно", "скрытые чаты, PIN, фальшивый и тревожный код"},
            {"💬", "Чаты", "метки, заметки, фокус, фильтр, поиск"},
            {"✉️", "Сообщения", "удалённые, правки, секунды, «Поделиться»"},
            {"📤", "Отправка", "отмена, тон, подтверждение, без звука"},
            {"🖼", "Медиа", "загрузка, истории, кружки, скачать всё"},
            {"🤖", "Нейросеть", "чат с ИИ, перевод, «Ответь за меня»"},
            {"🧩", "Плагины", "команды .calc, .tr, .ai и свои"},
            {"🎨", "Оформление", "магазин, темы, значки, иконки"},
            {"💼", "Бизнес", "кабинет, «Нет на месте», приветствие"},
            {"🎮", "Друзья и игры", "игры, друзья, достижения, итоги"},
            {"🚫", "Реклама и каналы", "реклама, админы, прокси"},
            {"⚙️", "Прочее", "резервная копия, очистка, обновления"},
    };

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean raw(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    /** Список разделов. */
    public static class Index extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "⭐ StarTegs — все разделы";
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader("Разделы"));
            for (int i = 0; i < SECTIONS.length; i++) {
                items.add(UItem.asButton(100 + i, SECTIONS[i][0] + "  " + SECTIONS[i][1], SECTIONS[i][2]));
            }
            items.add(UItem.asShadow("StarTegs " + StarModConfig.MOD_VERSION + ". Тариф: " + StarMod.tierTitle() + ". Функции с ⭐ — Premium."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int i = item.id - 100;
            if (i >= 0 && i < SECTIONS.length) presentFragment(new Screen(i));
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
                    button(items, 130, "🚫 Реклама, админы, каналы, истории, прокси");
                    break;
                default:
                    check(items, 140, "⭐ Кнопка StarTegs над списком чатов", StarMod.showChatsButton());
                    button(items, 141, "💾 Резервная копия настроек");
                    button(items, 142, "📊 Что сохранил мод");
                    button(items, 143, "🔄 Проверить обновления");
                    items.add(UItem.asButton(144, "Очистить сохранённое модом", "").red());
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
            switch (id) {
                case 1: StarTrial.offer(this, refresh); return;
                case 2: presentFragment(new StarPremiumActivity()); return;
                case 3: org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=premium"); return;
                case 10: StarMod.toggle("ghost_typing", false); break;
                case 11: StarMod.toggle("ghost_read", false); break;
                case 12: StarMod.toggle("ghost_online", false); break;
                case 13: StarMod.toggle("ghost_stories", false); break;
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
                case 42: presentFragment(new StarDeletedActivity()); return;
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
                default: return;
            }
            refresh.run();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
