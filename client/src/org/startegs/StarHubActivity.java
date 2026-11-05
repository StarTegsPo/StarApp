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
    private static final int ID_BRAND = 200;
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

        items.add(UItem.asHeader("Оформление"));
        items.add(UItem.asCheck(ID_BRAND, "Фирменный дизайн StarTegs").setChecked(StarTheme.isActive()));
        items.add(UItem.asCheck(ID_SECONDS, "Секунды во времени сообщений").setChecked(StarMod.showSeconds()));
        items.add(UItem.asButton(ID_TG_THEMES, R.drawable.msg_theme, "Темы, шрифт и пузыри сообщений"));
        items.add(UItem.asShadow("Фирменный дизайн — фиолетово-синяя тема StarTegs, светлая и тёмная. Выключите, чтобы вернуть прежнюю. "
                + "В «Темах» можно собрать свою: цвета, обои, размер шрифта, скругление сообщений."));

        items.add(UItem.asHeader("Сообщения"));
        items.add(UItem.asCheck(ID_SAVE_DELETED, "Сохранять удалённые сообщения").setChecked(StarMod.saveDeleted()));
        items.add(UItem.asCheck(ID_SAVE_EDITS, "Сохранять историю изменений").setChecked(StarMod.saveEdits()));
        items.add(UItem.asShadow("Удалённые остаются в чате с пометкой «🗑 удалено», старые версии правок — в меню сообщения."));

        items.add(UItem.asHeader("Приватность"));
        items.add(UItem.asCheck(ID_GHOST_TYPING, "Не показывать, что я печатаю").setChecked(StarMod.ghostTyping()));
        items.add(UItem.asCheck(ID_GHOST_READ, "Не отправлять «прочитано» в личных чатах").setChecked(StarMod.ghostRead()));
        items.add(UItem.asShadow("Собеседник не увидит «печатает…» и две галочки. На других ваших устройствах такие чаты останутся непрочитанными."));

        items.add(UItem.asHeader("Интерфейс"));
        items.add(UItem.asCheck(ID_HIDE_STORIES, "Скрыть истории над списком чатов").setChecked(StarMod.hideStories()));
        items.add(UItem.asCheck(ID_HIDE_PROMO, "Скрыть Premium, Business и подарки в настройках").setChecked(StarMod.hidePromo()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_STATS, R.drawable.msg_stats, "Что сохранил мод"));
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
        switch (id) {
            case ID_BRAND:
                StarTheme.setActive(this, !StarTheme.isActive());
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
