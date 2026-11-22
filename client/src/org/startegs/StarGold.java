/*
 * StarTegs 6.2 «Gold»: подписка StarTegs Gold за звёзды Telegram (49 ⭐ / 30 дней) через бота StarTegs Hub.
 * Всё, что было бесплатным, остаётся бесплатным; Gold добавляет «выделяющие» фишки: золотую отметку
 * и переливающееся имя для друзей, эксклюзивные анимированные рамки, эффекты отправки и стили интерфейса,
 * золотую карточку профиля и почти безлимитную Звёздочку. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class StarGold {

    private StarGold() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean active() {
        return prefs().getLong("gold_until", 0) > System.currentTimeMillis() / 1000;
    }

    static long until() { return prefs().getLong("gold_until", 0); }

    static int price() { return prefs().getInt("gold_price", 49); }

    static int days() { return prefs().getInt("gold_days", 30); }

    /** 6.3: пробный Gold ещё доступен (по словам бота). */
    static boolean trialAvailable() { return !active() && StarCloud.online() && prefs().getBoolean("gold_trial", true); }

    static int trialDays() { return prefs().getInt("gold_trial_days", 3); }

    static int refDays() { return prefs().getInt("gold_ref_days", 3); }

    public static String title() {
        return active() ? "👑 Gold до " + new SimpleDateFormat("d MMMM", new Locale("ru")).format(new Date(until() * 1000)) : "👑 Gold — " + price() + " ⭐";
    }

    /** Спросить у бота Hub, есть ли Gold (на старте и после покупки). */
    public static void refresh(int account, Runnable done) {
        if (!StarCloud.online()) return;
        StarCloud.call(account, "gold", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                prefs().edit().putLong("gold_until", res.optLong("gold_until", 0)).putInt("gold_price", res.optInt("price", 49))
                        .putInt("gold_days", res.optInt("days", 30)).putBoolean("gold_trial", res.optBoolean("trial", false))
                        .putInt("gold_trial_days", res.optInt("trial_days", 3)).putInt("gold_ref_days", res.optInt("ref_days", 3)).apply();
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (done != null) done.run();
            }
        });
    }

    /** true — Gold есть; иначе показываем, что даёт Gold, и возвращаем false. */
    public static boolean require(BaseFragment f, String feature) {
        if (active()) return true;
        if (f == null || f.getParentActivity() == null) return false;
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("👑 " + feature + " — в StarTegs Gold")
                .setMessage("Золотая отметка и переливающееся имя для друзей, эксклюзивные рамки, эффекты и стили. " + price() + " ⭐ за " + days() + " дней.")
                .setPositiveButton("Подробнее", (d, w) -> f.presentFragment(new Screen()))
                .setNegativeButton("Позже", null).show();
        return false;
    }

    public static void buy(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        if (!StarCloud.online()) {
            Toast.makeText(a, "Gold покупается через бота StarTegs Hub — он сейчас не подключён", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, "⏳ Открываю оплату…", Toast.LENGTH_SHORT).show();
        StarCloud.call(f.getCurrentAccount(), "gold_link", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                String link = res.optString("link");
                if (link.isEmpty() || f.getParentActivity() == null) return;
                Browser.openUrl(f.getParentActivity(), link);
                // после оплаты бот пришлёт сообщение; через минуту проверим статус сами
                AndroidUtilities.runOnUIThread(() -> refresh(f.getCurrentAccount(), done), 20_000);
                AndroidUtilities.runOnUIThread(() -> refresh(f.getCurrentAccount(), done), 60_000);
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 6.3: пробный период — один раз на аккаунт, бот проверяет сам. */
    static void trial(BaseFragment f, Runnable done) {
        if (f.getParentActivity() == null) return;
        StarCloud.call(f.getCurrentAccount(), "gold_trial", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                prefs().edit().putLong("gold_until", res.optLong("gold_until", 0)).putBoolean("gold_trial", false).apply();
                if (f.getParentActivity() != null) {
                    new AlertDialog.Builder(f.getParentActivity()).setTitle("🎁 Gold включён!")
                            .setMessage("Целых " + trialDays() + " дня: золотое имя ✦ у друзей, рамки Алмаз и Корона, эффекты и стили Gold. Попробуй всё!")
                            .setPositiveButton("Выбрать стиль", (d, w) -> f.presentFragment(new StarSkin.Screen()))
                            .setNegativeButton("Рамки", (d, w) -> f.presentFragment(new StarCosmetics.Shop())).show();
                }
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (why != null && why.contains("уже")) prefs().edit().putBoolean("gold_trial", false).apply();
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
                if (done != null) done.run();
            }
        });
    }

    /** 6.3: ссылка-приглашение: +N дней Gold за каждого нового друга. */
    static void invite(BaseFragment f) {
        if (f.getParentActivity() == null) return;
        if (!StarCloud.online()) {
            Toast.makeText(f.getParentActivity(), "Приглашения работают через бота StarTegs Hub", Toast.LENGTH_LONG).show();
            return;
        }
        StarCloud.call(f.getCurrentAccount(), "gold_ref", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                if (f.getParentActivity() == null) return;
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(android.content.Intent.EXTRA_TEXT, "Заходи в StarTegs ⭐ удалённые сообщения, нейросеть и стиль, которого нет у других: " + res.optString("link"));
                f.getParentActivity().startActivity(android.content.Intent.createChooser(i, "Пригласить друга (уже позвал: " + res.optInt("refs") + ")"));
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================================================================ имя друга с Gold

    static final int[] GOLD = {0xFFFFD86B, 0xFFFFB13B, 0xFFFF8A00, 0xFFFFE9A8};

    /** Переливающееся золотом имя и отметка ✦ (для друзей с Gold). */
    static CharSequence goldName(CharSequence name) {
        SpannableString sp = new SpannableString(name);
        long t = System.currentTimeMillis() / 600;
        for (int i = 0; i < sp.length(); i++) {
            sp.setSpan(new ForegroundColorSpan(GOLD[(int) ((i + t) % GOLD.length)]), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return android.text.TextUtils.concat(sp, " ✦");
    }

    // ================================================================ экран Gold

    static final String[][] PERKS = {
            {"✦", "Золотая отметка у имени", "Друзья со StarTegs видят ✦ рядом с твоим именем"},
            {"🌈", "Переливающееся имя", "Имя у друзей светится золотом"},
            {"💎", "Эксклюзивные рамки", "Алмаз, Корона, Аврора, Феникс — все анимированные"},
            {"🎆", "Эффекты отправки", "Фейерверк, Золотой дождь, Сердечный взрыв"},
            {"✨", "Стили интерфейса Gold Luxe и Diamond", "Самые дорогие на вид стили StarTegs"},
            {"🪪", "Золотая карточка профиля", "Золотое сияние и корона в твоей карточке и картинке для сторис"},
            {"🤖", "Звёздочка почти без лимита", "300 вопросов в день вместо 40 и в 3 раза больше памяти"},
            {"🛍", "Маркет: твои объявления первыми", "С отметкой 👑 Gold — их видят раньше всех"},
            {"📤", "Экспорт всего архива", "Удалённые и правки за всё время одним файлом"},
            {"💛", "Поддержка StarTegs", "Ты помогаешь делать приложение лучше"},
    };

    public static class Screen extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return "👑 StarTegs Gold";
        }

        @Override
        public View createView(Context context) {
            View v = super.createView(context);
            StarSilk.decorate(this, new int[]{0xFFFFC94D, 0xFFFF8A3D}, listView);
            refresh(currentAccount, this::update);
            return v;
        }

        @Override
        public boolean isLightStatusBar() {
            return !StarSilk.barsOn() && super.isLightStatusBar();
        }

        private void update() {
            if (listView != null) listView.adapter.update(true);
        }

        private View hero(Context ctx) {
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = AndroidUtilities.dp(22);
            card.setPadding(p, p, p, p);
            int[] a = {0xFFFFD86B, 0xFFFF9F1C}, b = {0xFFFFB13B, 0xFFFF6A88};
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, a.clone());
            bg.setCornerRadius(AndroidUtilities.dp(26));
            card.setBackground(bg);
            StarYou.breathe(card, bg, a, b);
            TextView crown = StarYou.text(ctx, "👑", 64, Color.WHITE, false);
            crown.setGravity(Gravity.CENTER);
            card.addView(crown);
            TextView t = StarYou.text(ctx, active() ? "Gold активен" : "Стань заметнее всех", 24, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            card.addView(t);
            TextView s = StarYou.text(ctx, active() ? "до " + new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date(until() * 1000))
                    : price() + " ⭐ за " + days() + " дней · продление добавляет дни", 14, 0xEEFFFFFF, false);
            s.setGravity(Gravity.CENTER);
            card.addView(s);
            StarSilk.card(card);
            FrameLayout wrap = new FrameLayout(ctx);
            int m = AndroidUtilities.dp(12);
            wrap.setPadding(m, m, m, AndroidUtilities.dp(6));
            wrap.addView(StarSilk.glow(card), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return wrap;
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            if (getParentActivity() != null) items.add(UItem.asCustom(hero(getParentActivity())));
            if (trialAvailable()) items.add(UItem.asButton(4, "🎁 Попробовать " + trialDays() + " дня бесплатно", "").accent());
            items.add(UItem.asButton(1, active() ? "Продлить ещё на " + days() + " дней — " + price() + " ⭐" : "Получить Gold — " + price() + " ⭐", "").accent());
            items.add(UItem.asHeader("Что даёт Gold"));
            for (int i = 0; i < PERKS.length; i++) {
                items.add(org.telegram.ui.SettingsActivity.SettingCell.Factory.of(100 + i, 0xFFFFC94D, 0xFFFF8A3D, org.telegram.messenger.R.drawable.settings_premium,
                        PERKS[i][0] + "  " + PERKS[i][1], PERKS[i][2]));
            }
            items.add(UItem.asButton(5, "👥 Позвать друга — +" + refDays() + " дня Gold", "за каждого"));
            if (active()) {
                items.add(UItem.asHeader("Включить золотые фишки"));
                items.add(UItem.asButton(2, "💎 Рамки и эффекты Gold → Магазин", ""));
                items.add(UItem.asButton(3, "✨ Стили Gold Luxe и Diamond", ""));
            }
            items.add(UItem.asShadow(StarCloud.online() ? "Оплата звёздами Telegram в боте StarTegs Hub. Отметку ✦ и имя видят друзья, у которых тоже StarTegs."
                    : "Gold покупается через бота StarTegs Hub — сейчас он не подключён. Все остальные функции StarTegs бесплатны."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            StarPolish.tap(view);
            if (item.id == 1) buy(this, this::update);
            else if (item.id == 2) presentFragment(new StarCosmetics.Shop());
            else if (item.id == 3) presentFragment(new StarSkin.Screen());
            else if (item.id == 4) trial(this, this::update);
            else if (item.id == 5) invite(this);
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }

    /** Баннер на главном экране StarTegs: переливающееся золото, по нажатию — экран Gold. */
    static View banner(BaseFragment f) {
        Context ctx = f.getParentActivity();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int p = AndroidUtilities.dp(14);
        card.setPadding(p, p, p, p);
        boolean on = active();
        int[] a = on ? new int[]{0xFF2B2108, 0xFF6B4E12} : new int[]{0xFFFFD86B, 0xFFFF9F1C}, b = on ? new int[]{0xFF3A2A00, 0xFFB8860B} : new int[]{0xFFFFB13B, 0xFFFF6A88};
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, a.clone());
        bg.setCornerRadius(AndroidUtilities.dp(20));
        card.setBackground(bg);
        StarYou.breathe(card, bg, a, b);
        card.addView(StarYou.text(ctx, "👑", 34, Color.WHITE, false));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(StarYou.text(ctx, on ? goldName("StarTegs Gold") : "StarTegs Gold", 17, on ? 0xFFFFD86B : Color.WHITE, true));
        col.addView(StarYou.text(ctx, on ? "активен до " + new SimpleDateFormat("d MMMM", new Locale("ru")).format(new Date(until() * 1000)) + " · твои золотые фишки →"
                : trialAvailable() ? "🎁 " + trialDays() + " дня бесплатно · золотое имя ✦, рамки и стили"
                : "Золотое имя ✦, эксклюзивные рамки и стили · " + price() + " ⭐", 13, 0xEEFFFFFF, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = AndroidUtilities.dp(12);
        card.addView(col, lp);
        card.setOnClickListener(v -> f.presentFragment(new Screen()));
        StarSilk.card(card);
        FrameLayout wrap = new FrameLayout(ctx);
        int m = AndroidUtilities.dp(12);
        wrap.setPadding(m, AndroidUtilities.dp(4), m, AndroidUtilities.dp(4));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    static int account() {
        return UserConfig.selectedAccount;
    }
}
