/*
 * StarTegs 3.0 — магазин и косметика: рамки на аватарку (их видят друзья с модом),
 * эффекты при отправке, темы и звуки на друзей. Покупки — за 💎 кристаллы в боте StarTegs,
 * Premium/VIP открывает всё. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class StarCosmetics {

    private StarCosmetics() {}

    public static final int MENU_SOUND = 9311;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- состояние (кэш ответа бота)

    private static JSONObject state() {
        try {
            return new JSONObject(prefs().getString("cos_state", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void saveState(JSONObject s) {
        prefs().edit().putString("cos_state", s.toString()).apply();
    }

    static boolean owns(String id) {
        JSONArray a = state().optJSONArray("owned");
        if (a == null) return id.equals("fr_star") || id.equals("fx_hearts") || id.equals("fx_confetti");
        for (int i = 0; i < a.length(); i++) if (id.equals(a.optString(i))) return true;
        return false;
    }

    static String myFrame() {
        return state().optString("frame", "");
    }

    /** Эффект, который мод добавляет к каждому отправленному тексту ("" — без эффекта). */
    public static String myEffect() {
        String e = prefs().getString("fx_local", null);
        return e != null ? e : state().optString("effect", "");
    }

    static void refresh(int account, Runnable done) {
        StarCloud.call(account, "cos_state", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                saveState(res);
                if (done != null) done.run();
            }

            @Override
            public void fail(String why) {
                if (done != null) done.run();
            }
        });
    }

    // ---------------------------------------------------------------- рамки друзей

    private static final HashMap<Long, String> frames = new HashMap<>();
    private static final HashSet<Long> pending = new HashSet<>();
    private static long lastFetch, loadedAt;
    private static boolean fetching;

    private static void loadFrames() {
        if (loadedAt != 0) return;
        loadedAt = System.currentTimeMillis();
        try {
            JSONObject o = new JSONObject(prefs().getString("cos_frames", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                frames.put(Long.parseLong(k), o.optString(k));
            }
        } catch (Exception ignore) {
        }
    }

    static String frameOf(long userId) {
        if (userId <= 0) return "";
        long self = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == self) return myFrame();
        loadFrames();
        String f = frames.get(userId);
        if (f == null || System.currentTimeMillis() - prefs().getLong("cos_frames_at", 0) > 6 * 3600_000L) {
            pending.add(userId);
            schedule();
        }
        return f == null ? "" : f;
    }

    private static void schedule() {
        if (fetching || pending.isEmpty() || StarModConfig.ECO_BOT.isEmpty()) return;
        long wait = Math.max(0, 30_000 - (System.currentTimeMillis() - lastFetch));
        fetching = true;
        AndroidUtilities.runOnUIThread(() -> {
            JSONArray ids = new JSONArray();
            for (Long id : pending) {
                ids.put(id);
                if (ids.length() >= 300) break;
            }
            pending.clear();
            lastFetch = System.currentTimeMillis();
            try {
                StarCloud.Result handler = new StarCloud.Result() {
                    boolean fallback;

                    @Override
                    public void ok(JSONObject res) {
                        fetching = false;
                        StarProfile.put(res.optJSONObject("profiles"));
                        JSONObject fr = res.optJSONObject("frames");
                        boolean changed = res.optJSONObject("profiles") != null && res.optJSONObject("profiles").length() > 0;
                        for (int i = 0; i < ids.length(); i++) {
                            long id = ids.optLong(i);
                            String f = fr != null ? fr.optString(String.valueOf(id), "") : "";
                            if (!f.equals(frames.get(id))) changed = true;
                            frames.put(id, f);
                        }
                        JSONObject all = new JSONObject();
                        try {
                            for (java.util.Map.Entry<Long, String> e : frames.entrySet()) all.put(String.valueOf(e.getKey()), e.getValue());
                        } catch (Exception ignore) {
                        }
                        prefs().edit().putString("cos_frames", all.toString()).putLong("cos_frames_at", System.currentTimeMillis()).apply();
                        if (changed) NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        schedule();
                    }

                    @Override
                    public void fail(String why) {
                        if (!fallback) { // бот v59 без витрин — берём только рамки
                            fallback = true;
                            try {
                                StarCloud.call(UserConfig.selectedAccount, "cos_get", new JSONObject().put("ids", ids), this);
                                return;
                            } catch (Exception ignore) {
                            }
                        }
                        fetching = false; // бот без v59 — тихо пропускаем
                        lastFetch = System.currentTimeMillis() + 10 * 60_000L;
                    }
                };
                StarCloud.call(UserConfig.selectedAccount, "prof_get", new JSONObject().put("ids", ids), handler);
            } catch (Exception e) {
                fetching = false;
            }
        }, wait);
    }

    private static final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint badge = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private static int[] colors(String frame) {
        switch (frame) {
            case "fr_fire": return new int[]{0xFFFFD000, 0xFFFF6A00, 0xFFE8152B, 0xFFFFD000};
            case "fr_neon": return new int[]{0xFFB44CFF, 0xFF00E5FF, 0xFFFF3DCB, 0xFFB44CFF};
            case "fr_snow": return new int[]{0xFFFFFFFF, 0xFF9BDCFF, 0xFFE6F6FF, 0xFFFFFFFF};
            case "fr_hearts": return new int[]{0xFFFF5C93, 0xFFFF9CC2, 0xFFFF3B6B, 0xFFFF5C93};
            case "fr_rainbow": return new int[]{0xFFFF3B3B, 0xFFFFB300, 0xFF3BD16F, 0xFF2D9CFF, 0xFFA24CFF, 0xFFFF3B3B};
            case "fr_gold": return new int[]{0xFFFFE082, 0xFFC99A2E, 0xFFFFF3C4, 0xFFFFE082};
            case "fr_galaxy": return new int[]{0xFF2B1B6E, 0xFF8A4CFF, 0xFF00C2FF, 0xFF2B1B6E};
            default: return new int[]{0xFF8E6BFF, 0xFF33A6FF, 0xFF8E6BFF};
        }
    }

    private static String badgeOf(String frame) {
        switch (frame) {
            case "fr_fire": return "🔥";
            case "fr_snow": return "❄️";
            case "fr_hearts": return "💖";
            case "fr_gold": return "👑";
            case "fr_galaxy": return "✨";
            default: return null;
        }
    }

    /** Вызывается из DialogCell после аватарки: рамка друга, если он выбрал её в StarTegs. */
    public static void drawFrame(Canvas canvas, RectF avatar, long dialogId, View cell) {
        try {
            if (dialogId <= 0 || avatar == null || avatar.width() <= 0) return;
            String f = frameOf(dialogId);
            float cx = avatar.centerX(), cy = avatar.centerY(), r = avatar.width() / 2f + AndroidUtilities.dp(2.5f);
            drawBadge(canvas, dialogId, cx, cy, r, cell);
            if (f == null || f.isEmpty()) return;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(AndroidUtilities.dp(2.6f));
            ring.setShader(new SweepGradient(cx, cy, colors(f), null));
            int shape = StarMod.avatarShape();
            if (shape == 0) canvas.drawCircle(cx, cy, r, ring);
            else {
                float rad = shape == 1 ? r * 0.55f : AndroidUtilities.dp(4);
                canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r, rad, rad, ring);
            }
            String b = badgeOf(f);
            if (b != null) {
                badge.setTextSize(AndroidUtilities.dp(f.equals("fr_gold") ? 15 : 12));
                badge.setTextAlign(Paint.Align.CENTER);
                if (f.equals("fr_gold")) canvas.drawText(b, cx, cy - r + AndroidUtilities.dp(3), badge);
                else canvas.drawText(b, cx + r * 0.72f, cy + r * 0.85f, badge);
            }
        } catch (Exception ignore) {
        }
    }

    private static final Paint badgeBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Статус друга или его эмодзи (пульсирует, когда он в сети) — в правом нижнем углу аватарки. */
    private static void drawBadge(Canvas canvas, long uid, float cx, float cy, float r, View cell) {
        boolean[] pulse = {false};
        String b = StarProfile.badge(uid, pulse);
        if (b == null || b.isEmpty()) return;
        float s = 1f;
        if (pulse[0]) {
            s = 1f + 0.14f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            if (cell != null) cell.postInvalidateDelayed(60);
        }
        float bx = cx + r * 0.70f, by = cy + r * 0.70f, br = AndroidUtilities.dp(10) * s;
        badgeBg.setColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
        canvas.drawCircle(bx, by, br, badgeBg);
        badge.setTextAlign(Paint.Align.CENTER);
        badge.setTextSize(AndroidUtilities.dp(12) * s);
        canvas.drawText(b, bx, by + AndroidUtilities.dp(4.3f) * s, badge);
    }

    // ---------------------------------------------------------------- темы из магазина

    // id → оттенок, градиент, неон
    private static final Object[][] THEMES = {
            {"th_sakura", 335f, true, false}, {"th_cyber", 290f, true, true}, {"th_ocean", 200f, true, false},
            {"th_matcha", 110f, false, false}, {"th_lava", 12f, true, true},
    };

    private static void applyTheme(String id) {
        for (Object[] t : THEMES) {
            if (!t[0].equals(id)) continue;
            prefs().edit().putBoolean("look_gradient", (Boolean) t[2]).putBoolean("look_neon", (Boolean) t[3]).apply();
            try {
                StarTheme.applyCustom((Float) t[1], (Boolean) t[3]);
            } catch (Exception e) {
                FileLog.e(e);
            }
            return;
        }
    }

    // ---------------------------------------------------------------- звуки на друзей

    static final Object[][] SOUNDS = {
            {"Кристалл 💎", R.raw.startegs_snd_crystal, true}, {"Пузырь 🫧", R.raw.startegs_snd_bubble, true},
            {"Капля 💧", R.raw.startegs_snd_drop, true}, {"Монетка 🪙", R.raw.startegs_snd_coin, false},
            {"Лазер 🔫", R.raw.startegs_snd_laser, false}, {"Сердцебиение 💓", R.raw.startegs_snd_heart, false},
            {"Магия 🪄", R.raw.startegs_snd_magic, false}, {"Ретро 👾", R.raw.startegs_snd_retro, false},
    };

    /** Копирует звук в «Уведомления» телефона и отдаёт ссылку, которую может проиграть система. */
    private static Uri soundUri(int index) throws Exception {
        String name = "StarTegs " + ((String) SOUNDS[index][0]).replaceAll("[^\\p{L}\\d ]", "").trim();
        String saved = prefs().getString("snd_uri_" + index, null);
        if (saved != null) return Uri.parse(saved);
        Context ctx = ApplicationLoader.applicationContext;
        Uri uri;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Audio.Media.DISPLAY_NAME, name + ".wav");
            v.put(MediaStore.Audio.Media.TITLE, name);
            v.put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav");
            v.put(MediaStore.Audio.Media.RELATIVE_PATH, "Notifications/StarTegs");
            v.put(MediaStore.Audio.Media.IS_NOTIFICATION, 1);
            uri = ctx.getContentResolver().insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) throw new IllegalStateException("MediaStore");
            try (InputStream in = ctx.getResources().openRawResource((Integer) SOUNDS[index][1]);
                 OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) os.write(b, 0, n);
            }
        } else {
            File dir = new File(ctx.getFilesDir(), "sounds");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "startegs_" + index + ".wav");
            try (InputStream in = ctx.getResources().openRawResource((Integer) SOUNDS[index][1]); OutputStream os = new FileOutputStream(f)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) os.write(b, 0, n);
            }
            uri = Uri.parse("file://" + f.getAbsolutePath()); // Telegram сам выдаст доступ системе (Android < 10)
        }
        prefs().edit().putString("snd_uri_" + index, uri.toString()).apply();
        return uri;
    }

    public static void pickSound(BaseFragment fragment, long dialogId) {
        if (fragment.getParentActivity() == null) return;
        CharSequence[] names = new CharSequence[SOUNDS.length + 1];
        boolean pack = owns("snd_pack");
        for (int i = 0; i < SOUNDS.length; i++) names[i] = SOUNDS[i][0] + ((Boolean) SOUNDS[i][2] || pack ? "" : "  🔒");
        names[SOUNDS.length] = "↩️ Обычный звук";
        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("🔔 Звук для этого чата")
                .setItems(names, (d, which) -> {
                    int account = fragment.getCurrentAccount();
                    SharedPreferences np = MessagesController.getNotificationsSettings(account);
                    String key = NotificationsController.getSharedPrefKey(dialogId, 0);
                    if (which == SOUNDS.length) {
                        np.edit().remove("sound_" + key).remove("sound_path_" + key).apply();
                    } else {
                        if (!(Boolean) SOUNDS[which][2] && !pack) {
                            Toast.makeText(fragment.getParentActivity(), "Набор «Звуки для друзей» — в магазине StarTegs", Toast.LENGTH_LONG).show();
                            fragment.presentFragment(new Shop());
                            return;
                        }
                        try {
                            Uri u = soundUri(which);
                            np.edit().putBoolean("custom_" + key, true).putString("sound_" + key, (String) SOUNDS[which][0])
                                    .putString("sound_path_" + key, u.toString()).apply();
                            android.media.RingtoneManager.getRingtone(fragment.getParentActivity(), u).play();
                        } catch (Exception e) {
                            FileLog.e(e);
                            Toast.makeText(fragment.getParentActivity(), "Не удалось поставить звук", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    NotificationsController.getInstance(account).deleteNotificationChannel(dialogId, 0);
                    Toast.makeText(fragment.getParentActivity(), which == SOUNDS.length ? "Обычный звук" : "🔔 Теперь этот чат звучит по-своему", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ---------------------------------------------------------------- магазин

    public static class Shop extends UniversalFragment {

        private static final int ID_TOPUP = 100;
        private static final int ID_REFRESH = 101;
        private static final int ID_FX_OFF = 102;
        private static final int ID_FRAME_OFF = 103;
        private static final int ID_ITEM = 1000;
        private final ArrayList<JSONObject> items = new ArrayList<>();
        private boolean loading = true;
        private String error;

        @Override
        protected CharSequence getTitle() {
            return "🛍 Магазин StarTegs";
        }

        @Override
        public boolean onFragmentCreate() {
            load();
            return super.onFragmentCreate();
        }

        private void load() {
            loading = true;
            StarCloud.call(currentAccount, "cos_state", null, new StarCloud.Result() {
                @Override
                public void ok(JSONObject res) {
                    saveState(res);
                    loading = false;
                    error = null;
                    if (listView != null) listView.adapter.update(true);
                }

                @Override
                public void fail(String why) {
                    loading = false;
                    error = why;
                    if (listView != null) listView.adapter.update(true);
                }
            });
        }

        private static String kindTitle(String k) {
            switch (k) {
                case "frame": return "Рамки на аватарку";
                case "effect": return "Эффекты при отправке";
                case "theme": return "Темы";
                default: return "Звуки";
            }
        }

        @Override
        protected void fillItems(ArrayList<UItem> list, UniversalAdapter adapter) {
            items.clear();
            JSONObject s = state();
            if (loading && !s.has("catalog")) {
                list.add(UItem.asShadow("⏳ Загружаю магазин…"));
                return;
            }
            if (error != null && !s.has("catalog")) {
                list.add(UItem.asShadow("⚠️ " + error + "\n\nМагазину нужен бот StarTegs версии v59 и хотя бы раз нажатый /start в @" + StarModConfig.ECO_BOT + "."));
                list.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, "Попробовать снова"));
                return;
            }
            boolean premium = s.optBoolean("premium");
            list.add(UItem.asButton(ID_TOPUP, R.drawable.msg_premium_badge, "💎 Кристаллы: " + s.optInt("crystals"), "пополнить").accent());
            list.add(UItem.asShadow(premium ? "У вас Premium — весь магазин открыт ✨"
                    : "Кристаллы покупаются за ⭐ Stars в боте @" + StarModConfig.ECO_BOT + ". Premium открывает всё сразу."));
            JSONArray cat = s.optJSONArray("catalog");
            String frame = myFrame(), fx = myEffect();
            String[] kinds = {"frame", "effect", "theme", "sound"};
            for (String k : kinds) {
                list.add(UItem.asHeader(kindTitle(k)));
                if (k.equals("frame")) list.add(UItem.asRadio(ID_FRAME_OFF, "Без рамки").setChecked(frame.isEmpty()));
                if (k.equals("effect")) list.add(UItem.asRadio(ID_FX_OFF, "Без эффекта").setChecked(fx.isEmpty()));
                for (int i = 0; cat != null && i < cat.length(); i++) {
                    JSONObject it = cat.optJSONObject(i);
                    if (it == null || !k.equals(it.optString("kind"))) continue;
                    String id = it.optString("id");
                    boolean own = owns(id);
                    String title = it.optString("emoji") + "  " + it.optString("title");
                    String value = own ? (id.equals(frame) || id.equals(fx) ? "✓ надето" : k.equals("sound") ? "✓ есть" : "надеть")
                            : it.optInt("price") == 0 ? "бесплатно" : it.optInt("price") + " 💎";
                    if (k.equals("theme") && own) value = "применить";
                    if (k.equals("frame") || k.equals("effect")) {
                        list.add(UItem.asRadio(ID_ITEM + items.size(), title, value).setChecked(id.equals(frame) || id.equals(fx)));
                    } else {
                        list.add(UItem.asButton(ID_ITEM + items.size(), title, value));
                    }
                    items.add(it);
                }
                if (k.equals("frame")) list.add(UItem.asShadow("Рамку видят друзья, у которых тоже StarTegs, — в своём списке чатов. Пригласи друзей 😉"));
                if (k.equals("effect")) list.add(UItem.asShadow("Эффект проигрывается при отправке у вас и у друга с модом, когда он в этом чате."));
                if (k.equals("sound")) list.add(UItem.asShadow("Поставить звук другу: профиль → ⋮ → «Свой звук для чата»."));
            }
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (getParentActivity() == null) return;
            if (item.id == ID_TOPUP) {
                Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=crystals");
                return;
            }
            if (item.id == ID_REFRESH) {
                load();
                return;
            }
            if (item.id == ID_FRAME_OFF || item.id == ID_FX_OFF) {
                equip(item.id == ID_FRAME_OFF ? "" : myFrame(), item.id == ID_FX_OFF ? "" : myEffect());
                return;
            }
            int i = item.id - ID_ITEM;
            if (i < 0 || i >= items.size()) return;
            JSONObject it = items.get(i);
            String id = it.optString("id"), kind = it.optString("kind");
            if (!owns(id)) {
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(it.optString("emoji") + " " + it.optString("title"))
                        .setMessage("Купить за " + it.optInt("price") + " 💎? Покупка навсегда.")
                        .setPositiveButton("Купить", (d, w) -> buy(id))
                        .setNegativeButton("Отмена", null)
                        .show();
                return;
            }
            if (kind.equals("frame")) equip(id, myEffect());
            else if (kind.equals("effect")) equip(myFrame(), id);
            else if (kind.equals("theme")) {
                applyTheme(id);
                Toast.makeText(getParentActivity(), "🎨 Тема «" + it.optString("title") + "» применена", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getParentActivity(), "Набор звуков уже ваш — поставьте звук другу в его профиле", Toast.LENGTH_LONG).show();
            }
        }

        private void buy(String id) {
            try {
                StarCloud.call(currentAccount, "cos_buy", new JSONObject().put("item", id), new StarCloud.Result() {
                    @Override
                    public void ok(JSONObject res) {
                        saveState(res);
                        if (getParentActivity() != null) Toast.makeText(getParentActivity(), "🎉 Готово! Теперь это ваше", Toast.LENGTH_SHORT).show();
                        if (id.startsWith("fr_")) StarAchieve.bump("frames", 1);
                        if (listView != null) listView.adapter.update(true);
                    }

                    @Override
                    public void fail(String why) {
                        if (getParentActivity() == null) return;
                        new AlertDialog.Builder(getParentActivity())
                                .setTitle("💎 Не хватает кристаллов")
                                .setMessage(why)
                                .setPositiveButton("Пополнить", (d, w) -> Browser.openUrl(getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=crystals"))
                                .setNegativeButton("Закрыть", null)
                                .show();
                    }
                });
            } catch (Exception ignore) {
            }
        }

        private void equip(String frame, String effect) {
            prefs().edit().remove("fx_local").apply();
            try {
                StarCloud.call(currentAccount, "cos_equip", new JSONObject().put("frame", frame).put("effect", effect), new StarCloud.Result() {
                    @Override
                    public void ok(JSONObject res) {
                        saveState(res);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        if (listView != null) listView.adapter.update(true);
                    }

                    @Override
                    public void fail(String why) {
                        if (getParentActivity() != null) Toast.makeText(getParentActivity(), why, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception ignore) {
            }
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
