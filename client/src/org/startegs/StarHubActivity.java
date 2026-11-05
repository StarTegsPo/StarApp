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
            items.add(UItem.asShadow("Также: долгое нажатие на сообщение → «Спросить ИИ», меню чата ⋮ → «Что я пропустил?» и «Улучшить мой текст». Лимиты и Premium — из @" + StarModConfig.AI_BOT + "."));
        }
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
        items.add(UItem.asShadow("У каждой палитры есть светлый и тёмный вариант — они переключаются вместе с ночным режимом Telegram. При выключении вернётся прежняя тема."));
        items.add(UItem.asHeader("Настройки вида"));
        items.add(UItem.asCheck(ID_SECONDS, "Секунды во времени сообщений").setChecked(StarMod.showSeconds()));
        items.add(UItem.asButton(ID_TG_THEMES, R.drawable.msg_theme, "Темы, шрифт и пузыри сообщений"));
        items.add(UItem.asShadow("В «Темах» можно собрать свою: цвета, обои, размер шрифта, скругление сообщений."));

        items.add(UItem.asHeader("Сообщения"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые сообщения").setChecked(StarMod.saveDeleted()));
        items.add(UItem.asCheck(ID_SAVE_EDITS, "Сохранять историю изменений").setChecked(StarMod.saveEdits()));
        items.add(UItem.asCheck(ID_ARCHIVE, "Копировать удалённые в облачный архив бота").setChecked(StarCloud.archiveEnabled()));
        if (StarCloud.archiveEnabled()) {
            items.add(UItem.asButton(ID_ARCHIVE_NOW, R.drawable.msg_download, "Отправить в архив сейчас"));
        }
        items.add(UItem.asButton(ID_DELETED_LIST, R.drawable.msg_delete, "Все удалённые сообщения"));
        items.add(UItem.asButton(ID_NOTES, R.drawable.msg_edit, "📝 Мои заметки", StarTools.allNotes().isEmpty() ? "" : String.valueOf(StarTools.allNotes().size())));
        items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑 удалено», старые версии правок — в меню сообщения. Облачный архив отправляет удалённые в ваш бот StarTegs, где их можно искать."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asCheck(ID_GHOST_TYPING, pro("Не показывать, что я печатаю")).setChecked(StarMod.ghostTyping()));
        items.add(UItem.asCheck(ID_GHOST_READ, pro("Не отправлять «прочитано» в личных чатах")).setChecked(StarMod.ghostRead()));
        items.add(UItem.asCheck(ID_GHOST_ONLINE, pro("Не показывать «в сети»")).setChecked(StarMod.ghostOnline()));
        items.add(UItem.asCheck(ID_GHOST_STORIES, pro("Смотреть истории тайно")).setChecked(StarMod.ghostStories()));
        items.add(UItem.asShadow("Собеседник не увидит «печатает…», две галочки и вас в сети, а автор истории — вас среди просмотревших. "
                + "Чаты без «прочитано» останутся непрочитанными на других ваших устройствах."));

        items.add(UItem.asHeader("Фильтр"));
        items.add(UItem.asCheck(ID_FILTER, "Скрывать сообщения по фильтру").setChecked(StarMod.filterEnabled()));
        int words = StarMod.filterWordsText().isEmpty() ? 0 : StarMod.filterWordsText().split("[\\n,]").length;
        items.add(UItem.asButton(ID_FILTER_WORDS, R.drawable.msg_edit, "Слова для фильтра", words > 0 ? String.valueOf(words) : "нет"));
        if (StarMod.filteredUsersCount() > 0) {
            items.add(UItem.asButton(ID_FILTER_USERS_CLEAR, R.drawable.msg_delete, "Снова показывать скрытых людей", String.valueOf(StarMod.filteredUsersCount())));
        }
        items.add(UItem.asShadow("Текст подходящих сообщений заменяется пометкой «скрыто фильтром». Скрыть все сообщения человека — профиль → ⋮ → «Скрывать его сообщения»."));

        items.add(UItem.asHeader(StarMod.isPro() ? "Закрытые и скрытые чаты" : "Закрытые и скрытые чаты  🔒"));
        items.add(UItem.asButton(ID_HIDDEN, R.drawable.msg_permissions, "🙈 Скрытые чаты", StarHidden.count() > 0 ? String.valueOf(StarHidden.count()) : ""));
        items.add(UItem.asButton(ID_LOCK_PIN, R.drawable.msg_permissions, StarLock.hasPin() ? "Сменить PIN-код" : "Задать PIN-код"));
        if (StarLock.lockedCount() > 0) {
            items.add(UItem.asButton(ID_LOCK_CLEAR, R.drawable.msg_delete, "Снять все замки", String.valueOf(StarLock.lockedCount())));
        }
        items.add(UItem.asShadow("Закрыть или скрыть чат: профиль чата → ⋮ → «Закрыть чат PIN-кодом» или «Скрыть чат из списка». Скрытые открываются здесь по PIN."));

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
        switch (id) {
            case ID_NOTES:
                presentFragment(new StarNotesActivity());
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
