/*
 * StarTegs 4.1 — «Темы от пользователей»: соберите свою тему (цвет + градиентные пузыри + неон),
 * опубликуйте её и продавайте другим за 💎 (автор получает 70 %). Данные — бот StarTegs v61.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

public class StarStyleShop extends UniversalFragment {

    private static final int ID_PUBLISH = 1;
    private static final int ID_SORT_TOP = 2;
    private static final int ID_SORT_NEW = 3;
    private static final int ID_REFRESH = 4;
    private static final int ID_ITEM = 1000;
    static final int[] PRICES = {0, 10, 25, 50, 100};

    private final ArrayList<JSONObject> themes = new ArrayList<>();
    private String sort = "top";
    private boolean loading = true;
    private String error;
    private int crystals = -1;

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return "🎨 Темы от пользователей";
    }

    private void refresh() {
        if (listView != null) listView.adapter.update(true);
    }

    private void load() {
        loading = true;
        try {
            StarCloud.call(currentAccount, "theme_list", new JSONObject().put("sort", sort), new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    loading = false;
                    error = null;
                    themes.clear();
                    crystals = res.optInt("crystals", -1);
                    JSONArray a = res.optJSONArray("themes");
                    for (int i = 0; a != null && i < a.length(); i++) if (a.optJSONObject(i) != null) themes.add(a.optJSONObject(i));
                    refresh();
                }

                @Override
                public void fail(String why) {
                    loading = false;
                    error = why;
                    refresh();
                }
            });
        } catch (Exception e) {
            loading = false;
        }
    }

    private static String hueName(int hue) {
        int best = 0, bestD = 999;
        for (int i = 0; i < StarTheme.HUES.length; i++) {
            int h = (Integer) StarTheme.HUES[i][1];
            int d = Math.abs(h - hue);
            d = Math.min(d, 360 - d);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return ((String) StarTheme.HUES[best][0]).split(" ")[0];
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_PUBLISH, R.drawable.msg_palette, "➕ Опубликовать свою тему").accent());
        items.add(UItem.asShadow("Тема = цвет StarTegs + градиентные пузыри + неон. Продавайте за 💎 — автору 70 % с каждой покупки"
                + (crystals >= 0 ? ". У вас " + crystals + " 💎." : ".")));
        items.add(UItem.asRadio(ID_SORT_TOP, "🔥 Популярные").setChecked("top".equals(sort)));
        items.add(UItem.asRadio(ID_SORT_NEW, "🆕 Новые").setChecked("new".equals(sort)));
        if (loading && themes.isEmpty()) {
            items.add(UItem.asShadow("⏳ Загружаю темы…"));
            return;
        }
        if (error != null && themes.isEmpty()) {
            items.add(UItem.asShadow("⚠️ " + error + "\n\nМагазину тем нужен бот StarTegs v61."));
            items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Попробовать снова"));
            return;
        }
        items.add(UItem.asHeader("Темы"));
        if (themes.isEmpty()) items.add(UItem.asShadow("Пока пусто — опубликуйте первую тему!"));
        for (int i = 0; i < themes.size(); i++) {
            JSONObject t = themes.get(i);
            String title = hueName(t.optInt("hue")) + "  " + t.optString("name")
                    + (t.optBoolean("gradient") ? " · градиент" : "") + (t.optBoolean("neon") ? " · неон" : "");
            String value = t.optBoolean("mine") ? "ваша · " + t.optInt("sales") + " 🛒"
                    : t.optBoolean("owned") || t.optInt("price") == 0 ? "применить" : t.optInt("price") + " 💎";
            items.add(UItem.asButton(ID_ITEM + i, title, value));
        }
        items.add(UItem.asShadow("Купленные темы остаются навсегда. Нажмите на свою или купленную тему, чтобы применить."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (getParentActivity() == null) return;
        switch (item.id) {
            case ID_PUBLISH: publish(); return;
            case ID_SORT_TOP: sort = "top"; load(); refresh(); return;
            case ID_SORT_NEW: sort = "new"; load(); refresh(); return;
            case ID_REFRESH: load(); refresh(); return;
        }
        int i = item.id - ID_ITEM;
        if (i < 0 || i >= themes.size()) return;
        JSONObject t = themes.get(i);
        if (t.optBoolean("mine") || t.optBoolean("owned") || t.optInt("price") == 0) {
            apply(t);
            return;
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle("🎨 " + t.optString("name"))
                .setMessage("Купить тему за " + t.optInt("price") + " 💎? Покупка навсегда, 70 % получит автор.")
                .setPositiveButton("Купить", (d, w) -> buy(t))
                .setNegativeButton("Отмена", null).show();
    }

    private void apply(JSONObject t) {
        StarCosmetics.applyStyle(t.optInt("hue"), t.optBoolean("gradient"), t.optBoolean("neon"));
        if (getParentActivity() != null) Toast.makeText(getParentActivity(), "🎨 Тема «" + t.optString("name") + "» применена", Toast.LENGTH_SHORT).show();
    }

    private void buy(JSONObject t) {
        try {
            StarCloud.call(currentAccount, "theme_buy", new JSONObject().put("id", t.optLong("id")), new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    try {
                        t.put("owned", true);
                    } catch (Exception ignore) {
                    }
                    crystals = res.optInt("crystals", crystals);
                    apply(t);
                    refresh();
                }

                @Override
                public void fail(String why) {
                    if (getParentActivity() == null) return;
                    new AlertDialog.Builder(getParentActivity())
                            .setTitle("💎 Не получилось")
                            .setMessage(why)
                            .setPositiveButton("Пополнить", (d, w) -> Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=crystals"))
                            .setNegativeButton("Закрыть", null).show();
                }
            });
        } catch (Exception ignore) {
        }
    }

    private void publish() {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        CharSequence[] hues = new CharSequence[StarTheme.HUES.length];
        for (int i = 0; i < hues.length; i++) hues[i] = (String) StarTheme.HUES[i][0];
        new AlertDialog.Builder(ctx)
                .setTitle("1/3 · Цвет темы")
                .setItems(hues, (d, which) -> {
                    int hue = (Integer) StarTheme.HUES[which][1];
                    askName(hue);
                }).show();
    }

    private void askName(int hue) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setHint("Например: Ночной Токио");
        new AlertDialog.Builder(ctx)
                .setTitle("2/3 · Название")
                .setMessage("Градиентные пузыри и неон берутся из ваших текущих настроек оформления.")
                .setView(input)
                .setPositiveButton("Дальше", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.length() < 2) {
                        Toast.makeText(ApplicationLoader.applicationContext, "Название слишком короткое", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    askPrice(hue, name.length() > 32 ? name.substring(0, 32) : name);
                })
                .setNegativeButton("Отмена", null).show();
    }

    private void askPrice(int hue, String name) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        CharSequence[] items = new CharSequence[PRICES.length];
        for (int i = 0; i < PRICES.length; i++) items[i] = PRICES[i] == 0 ? "Бесплатно" : PRICES[i] + " 💎";
        new AlertDialog.Builder(ctx)
                .setTitle("3/3 · Цена")
                .setItems(items, (d, which) -> {
                    try {
                        JSONObject body = new JSONObject().put("name", name).put("hue", hue)
                                .put("gradient", StarLook.gradientBubbles()).put("neon", StarLook.neon()).put("price", PRICES[which]);
                        StarCloud.call(currentAccount, "theme_pub", body, new StarCloud.Result() {
                            @Override
                            public void ok(JSONObject res) {
                                Toast.makeText(ApplicationLoader.applicationContext, "🎉 Тема «" + name + "» опубликована", Toast.LENGTH_SHORT).show();
                                sort = "new";
                                load();
                            }

                            @Override
                            public void fail(String why) {
                                Toast.makeText(ApplicationLoader.applicationContext, why, Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception ignore) {
                    }
                }).show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
