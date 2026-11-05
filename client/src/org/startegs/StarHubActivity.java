/*
 * StarTegs — экран «StarTegs»: сервисы экосистемы, фирменное оформление и настройки,
 * которые пользователь меняет сам. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.R;
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
            items.add(UItem.asRadio(ID_PALETTE_OFF + 1 + i, "StarTegs · " + StarTheme.PALETTES[i][1]).setChecked(active == i));
        }
        items.add(UItem.asShadow("У каждой палитры есть светлый и тёмный вариант — они переключаются вместе с ночным режимом Telegram. При выключении вернётся прежняя тема."));
        items.add(UItem.asHeader("Настройки вида"));
        items.add(UItem.asCheck(ID_SECONDS, "Секунды во времени сообщений").setChecked(StarMod.showSeconds()));
        items.add(UItem.asButton(ID_TG_THEMES, R.drawable.msg_theme, "Темы, шрифт и пузыри сообщений"));
        items.add(UItem.asShadow("В «Темах» можно собрать свою: цвета, обои, размер шрифта, скругление сообщений."));

        items.add(UItem.asHeader("Сообщения"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые сообщения").setChecked(StarMod.saveDeleted()));
        items.add(UItem.asCheck(ID_SAVE_EDITS, "Сохранять историю изменений").setChecked(StarMod.saveEdits()));
        items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑 удалено», старые версии правок — в меню сообщения."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asCheck(ID_GHOST_TYPING, "Не показывать, что я печатаю").setChecked(StarMod.ghostTyping()));
        items.add(UItem.asCheck(ID_GHOST_READ, "Не отправлять «прочитано» в личных чатах").setChecked(StarMod.ghostRead()));
        items.add(UItem.asCheck(ID_GHOST_ONLINE, "Не показывать «в сети»").setChecked(StarMod.ghostOnline()));
        items.add(UItem.asCheck(ID_GHOST_STORIES, "Смотреть истории тайно").setChecked(StarMod.ghostStories()));
        items.add(UItem.asShadow("Собеседник не увидит «печатает…», две галочки и вас в сети, а автор истории — вас среди просмотревших. "
                + "Чаты без «прочитано» останутся непрочитанными на других ваших устройствах."));

        items.add(UItem.asHeader("Интерфейс"));
        items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории над списком чатов").setChecked(StarMod.hideStories()));
        items.add(UItem.asCheck(ID_HIDE_PROMO, "Скрыть Premium, Business и подарки в настройках").setChecked(StarMod.hidePromo()));
        items.add(UItem.asShadow(null));

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
        switch (id) {
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
