/*
 * StarTegs 4.0 — экран «Premium-функции»: что из Telegram Premium открыто в моде,
 * переключатели новых функций и честный список того, что проверяет только сервер Telegram.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

public class StarPremiumActivity extends UniversalFragment {

    private static final int ID_FAST_DL = 1;
    private static final int ID_SAVE_STORIES = 2;
    private static final int ID_AWAY = 3;
    private static final int ID_AWAY_TEXT = 4;
    private static final int ID_GREET = 5;
    private static final int ID_GREET_TEXT = 6;
    private static final int ID_ICONS = 7;
    private static final int ID_HUB = 8;
    private static final int ID_PLUS = 9;

    @Override
    protected CharSequence getTitle() {
        return "⭐ Premium-функции";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Открыто в StarTegs 4.0"));
        items.add(UItem.asCheck(ID_FAST_DL, "🚀 Ускоренная загрузка файлов").setChecked(StarPremium.fastDownload()));
        items.add(UItem.asCheck(ID_SAVE_STORIES, "📥 Сохранять истории в галерею").setChecked(StarPremium.saveStories()));
        items.add(UItem.asShadow("Загрузка идёт в несколько потоков, как у Premium (сервер может ограничивать скорость). "
                + "Истории: ⋮ → «Сохранить в галерею», если автор не запретил пересылку."));

        items.add(UItem.asHeader("💼 Как в Telegram Business"));
        items.add(UItem.asCheck(ID_AWAY, "🌙 Автоответ «Нет на месте»").setChecked(StarPremium.awayOn()));
        items.add(UItem.asButton(ID_AWAY_TEXT, R.drawable.msg_edit, "Текст автоответа"));
        items.add(UItem.asCheck(ID_GREET, "👋 Приветствие новым собеседникам").setChecked(StarPremium.greetOn()));
        items.add(UItem.asButton(ID_GREET_TEXT, R.drawable.msg_edit, "Текст приветствия"));
        items.add(UItem.asShadow("Отвечает в личных чатах людям (не ботам), пока приложение работает. «Нет на месте» — не чаще раза в 6 часов "
                + "на человека. Приветствие — один раз каждому, кого нет в контактах. Быстрые ответы — «Шаблоны ответов» в меню чата."));

        items.add(UItem.asHeader("Уже было в StarTegs"));
        items.add(UItem.asButton(ID_ICONS, R.drawable.msg_premium_badge, "🎨 Иконки приложения"));
        items.add(UItem.asShadow("✅ Перевод целых чатов — кнопка «Перевести» над чатом (через ИИ)\n"
                + "✅ Расшифровка голосовых и кружков — долгое нажатие → «Расшифровать (ИИ)»\n"
                + "✅ Без рекламы — Настройки → «Реклама, каналы, прокси»\n"
                + "✅ Режим инкогнито в историях и скрытое «прочитано» — StarTegs → Приватность\n"
                + "✅ Теги для сохранённого — «Избранные сообщения ⭐» и метки чатов\n"
                + "✅ До 10 аккаунтов вместо 3\n"
                + "✅ Статус и витрина профиля для друзей с модом\n"
                + "✅ Перевод сообщений и своего текста — в 🧪 «Новое»\n"
                + "⭐ 4.1: «Ответь за меня» без лимита, поиск по смыслу, «отправить, когда появится в сети», шторка приватности"));
        items.add(UItem.asButton(ID_PLUS, R.drawable.msg_fave, "🧪 Новое в StarTegs").accent());

        items.add(UItem.asHeader("Только с настоящим Premium"));
        items.add(UItem.asShadow("Эти функции проверяет сервер Telegram, поэтому никакой мод их не включит: "
                + "файлы до 4 ГБ, больше папок и закрепов, премиум-стикеры и реакции, эмодзи-статус, цвет имени и профиля, "
                + "анимированные аватарки, скрытие времени посещения с просмотром чужого."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Runnable refresh = () -> {
            if (listView != null) listView.adapter.update(true);
        };
        switch (item.id) {
            case ID_FAST_DL:
                StarPremium.toggle("prem_fast_dl", true);
                break;
            case ID_SAVE_STORIES:
                StarPremium.toggle("prem_save_stories", true);
                break;
            case ID_AWAY:
                StarPremium.toggle("prem_away", false);
                break;
            case ID_GREET:
                StarPremium.toggle("prem_greet", false);
                break;
            case ID_AWAY_TEXT:
                StarPremium.editText(this, true, refresh);
                return;
            case ID_GREET_TEXT:
                StarPremium.editText(this, false, refresh);
                return;
            case ID_ICONS:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                return;
            case ID_PLUS:
                presentFragment(new StarPlusActivity());
                return;
            case ID_HUB:
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
