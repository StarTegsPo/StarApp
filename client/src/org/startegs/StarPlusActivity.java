/*
 * StarTegs 4.0 — экран «Новое в 4.0»: функции в духе WhiteGram (перевод, плагины-команды,
 * история профиля, цензор, двойное дно, «прочитать всё»).
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class StarPlusActivity extends UniversalFragment {

    private static final int ID_PLUGINS = 1;
    private static final int ID_PLUGINS_HELP = 2;
    private static final int ID_MACROS = 3;
    private static final int ID_REPEAT = 4;
    private static final int ID_CENSOR = 5;
    private static final int ID_PROFILE = 6;
    private static final int ID_READ_ALL = 7;
    private static final int ID_SECURE = 8;
    private static final int ID_RECENTS = 9;
    private static final int ID_PANIC = 10;
    private static final int ID_PREMIUM = 11;
    private static final int ID_SEARCH = 12;
    private static final int ID_SHADE = 13;
    private static final int ID_KEYBOARD = 14;
    private static final int ID_TRIAL = 15;
    private static final int ID_STYLES = 16;

    @Override
    protected CharSequence getTitle() {
        return "🧪 Новое в StarTegs";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_PREMIUM, R.drawable.msg_premium_badge, "⭐ Premium-функции").accent());
        if (StarTrial.available()) items.add(UItem.asButton(ID_TRIAL, R.drawable.msg_fave, "🎁 Premium 3 дня бесплатно").accent());
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("✨ Новое в 4.1"));
        items.add(UItem.asButton(ID_SEARCH, R.drawable.msg_search, "🔎 Поиск по смыслу во всех чатах", StarMod.isPro() ? "" : "⭐"));
        items.add(UItem.asCheck(ID_SHADE, "🫥 Шторка приватности" + (StarMod.isPro() ? "" : "  ⭐")).setChecked(StarShade.enabled()));
        items.add(UItem.asCheck(ID_KEYBOARD, "⌨️ Секретная клавиатура").setChecked(StarPlus.incognitoKeyboard()));
        items.add(UItem.asButton(ID_STYLES, R.drawable.msg_palette, "🎨 Темы от пользователей"));
        items.add(UItem.asShadow("💡 «Ответь за меня»: долгое нажатие на сообщение — 3 варианта ответа в вашем стиле (" + StarReply.FREE_PER_DAY + " в день бесплатно). "
                + "📨 «Отправить, когда появится в сети»: меню ⋮ в личном чате. "
                + "🫥 Шторка закрывает экран, когда телефон кладут на стол или переворачивают. "
                + "⌨️ Секретная клавиатура просит клавиатуру не запоминать ввод (не все клавиатуры это поддерживают; Samsung — нет). "
                + "Шаблоны теперь можно вставлять, написав «/название»."));

        items.add(UItem.asHeader("🧩 Плагины-команды"));
        items.add(UItem.asCheck(ID_PLUGINS, "Команды в чате (.calc, .tr, .ai …)").setChecked(StarPlugins.enabled()));
        items.add(UItem.asButton(ID_PLUGINS_HELP, R.drawable.msg_info, "Список команд"));
        items.add(UItem.asButton(ID_MACROS, R.drawable.msg_edit, "Мои команды", StarPlugins.macroCount() > 0 ? String.valueOf(StarPlugins.macroCount()) : ""));
        items.add(UItem.asShadow("Напишите в любом чате «.calc 2+2*2» или «.tr en Привет» — уйдёт готовый результат. Свои команды: «.адрес» → ваш текст."));

        items.add(UItem.asHeader("💬 Чаты"));
        items.add(UItem.asCheck(ID_REPEAT, "🔁 «Повторить (+1)» в меню сообщения").setChecked(StarPlus.repeatOn()));
        items.add(UItem.asCheck(ID_CENSOR, "🤐 Скрывать мат звёздочками").setChecked(StarPlus.censorOn()));
        items.add(UItem.asCheck(ID_PROFILE, "🕓 Записывать историю профилей").setChecked(StarPlus.profileHistoryOn()));
        items.add(UItem.asButton(ID_READ_ALL, R.drawable.msg_markread, "✔️ Прочитать все чаты"));
        items.add(UItem.asShadow("Перевод: долгое нажатие на сообщение → «Перевести (StarTegs)», меню чата ⋮ → «Перевести мой текст». "
                + "Там же — «К первому сообщению». История профиля: профиль → ⋮ → «История профиля» (смена имени, @username и аватарки). "
                + "Цензор применяется к вновь загруженным сообщениям."));

        items.add(UItem.asHeader("🕳 Двойное дно"));
        items.add(UItem.asCheck(ID_SECURE, "📵 Запрет скриншотов в закрытых и скрытых чатах").setChecked(StarPlus.secureLocked()));
        items.add(UItem.asCheck(ID_RECENTS, "🙈 Скрывать StarTegs из «Недавних»").setChecked(StarPlus.hideRecents()));
        items.add(UItem.asButton(ID_PANIC, R.drawable.msg_secret, "🚨 Тревожный код", StarPlus.hasPanic() ? "задан" : ""));
        items.add(UItem.asShadow("Тревожный код вводится на экране блокировки вместо настоящего: мод стирает всё, что сохранил, "
                + "и открывает «пустой» режим, как фальшивый код. Нужен включённый код-пароль Telegram."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Runnable refresh = () -> {
            if (listView != null) listView.adapter.update(true);
        };
        switch (item.id) {
            case ID_PREMIUM:
                presentFragment(new StarPremiumActivity());
                return;
            case ID_TRIAL:
                StarTrial.offer(this, refresh);
                return;
            case ID_SEARCH:
                presentFragment(new StarSearchActivity());
                return;
            case ID_STYLES:
                presentFragment(new StarStyleShop());
                return;
            case ID_SHADE:
                if (!StarMod.requirePro(this, "Шторка приватности")) return;
                StarMod.toggle("shade_on", false);
                StarShade.update();
                break;
            case ID_KEYBOARD:
                StarPlus.toggle("plus_incognito_kb", false);
                if (getParentActivity() != null) android.widget.Toast.makeText(getParentActivity(), "Применится в чатах, открытых заново", android.widget.Toast.LENGTH_SHORT).show();
                break;
            case ID_PLUGINS:
                StarMod.toggle("plugins_on", true);
                break;
            case ID_PLUGINS_HELP:
                if (getParentActivity() != null) {
                    new AlertDialog.Builder(getParentActivity()).setTitle("🧩 Команды StarTegs").setMessage(StarPlugins.HELP).setPositiveButton("OK", null).show();
                }
                return;
            case ID_MACROS:
                StarPlugins.editMacros(this, refresh);
                return;
            case ID_REPEAT:
                StarPlus.toggle("plus_repeat", true);
                break;
            case ID_CENSOR:
                StarPlus.toggle("plus_censor", false);
                break;
            case ID_PROFILE:
                StarPlus.toggle("plus_profile_history", true);
                break;
            case ID_READ_ALL:
                StarPlus.readAll(this);
                return;
            case ID_SECURE:
                StarPlus.toggle("plus_secure_locked", true);
                break;
            case ID_RECENTS:
                StarPlus.toggle("plus_hide_recents", false);
                StarPlus.applyRecents(getParentActivity());
                break;
            case ID_PANIC:
                StarPlus.setupPanic(this, refresh);
                return;
            default:
                return;
        }
        refresh.run();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
