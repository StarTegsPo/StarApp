/*
 * StarTegs 4.4 — «Стиль»: то, что видят ВСЕ, даже без мода.
 *   • красивый шрифт для сообщений (𝓢𝓽𝔂𝓵𝓮, 𝔽𝕒𝕟𝕔𝕪, з а з о р, з̶а̶ч̶ё̶р̶к̶, кАпСлОк…) — в поле ввода или всегда;
 *   • украшенное имя в профиле (꧁ Имя ꧂, ★彡 Имя 彡★ …);
 *   • крутое «О себе» от нейросети;
 *   • «Красивый скрин»: сообщение — в стильную карточку для историй.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public final class StarStyle {

    private StarStyle() {}

    public static final int MENU_FONT = 9350;
    public static final int OPTION_CARD = 9060;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    // ================================================================ шрифты

    /** Название и пример. Латиница — математические буквы Unicode, кириллица — приёмы, которые работают везде. */
    static final String[] STYLES = {
            "𝓢𝓬𝓻𝓲𝓹𝓽 — рукописный (латиница)",
            "𝕯𝖆𝖗𝖐 — готика (латиница)",
            "𝔻𝕠𝕦𝕓𝕝𝕖 — контур (латиница)",
            "𝗕𝗼𝗹𝗱 — жирный (латиница)",
            "ⓑⓤⓑⓑⓛⓔ — кружочки (латиница)",
            "кАк ТуТ — лесенка",
            "р а з р я д к а",
            "з̶а̶ч̶ё̶р̶к̶н̶у̶т̶о̶",
            "п̲о̲д̲ч̲ё̲р̲к̲н̲у̲т̲о̲",
            "ꜱᴍᴀʟʟ ᴄᴀᴘꜱ — капитель (латиница)",
            "★ Звёзды ★ между ★ словами",
            "сТиЛь — греческий микс (αвcδєƒ)",
    };

    private static String mapAlpha(String s, int upper, int lower, int digit) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z' && upper > 0) sb.appendCodePoint(upper + (c - 'A'));
            else if (c >= 'a' && c <= 'z' && lower > 0) sb.appendCodePoint(lower + (c - 'a'));
            else if (c >= '0' && c <= '9' && digit > 0) sb.appendCodePoint(digit + (c - '0'));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String doubleStruck(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) { // буквы, которые в Unicode живут отдельно
                case 'C': sb.append('ℂ'); continue;
                case 'H': sb.append('ℍ'); continue;
                case 'N': sb.append('ℕ'); continue;
                case 'P': sb.append('ℙ'); continue;
                case 'Q': sb.append('ℚ'); continue;
                case 'R': sb.append('ℝ'); continue;
                case 'Z': sb.append('ℤ'); continue;
            }
            sb.append(mapAlpha(String.valueOf(c), 0x1D538, 0x1D552, 0x1D7D8));
        }
        return sb.toString();
    }

    private static String combine(String s, char mark) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c);
            if (!Character.isWhitespace(c)) sb.append(mark);
        }
        return sb.toString();
    }

    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String GREEK_FROM = "аАвВеЕкКмМнНоОрРсСтТуУхХиИлЛпПaAbBcCeEnNoOpPtTuUxX";
    private static final String GREEK_TO = "αΑвΒєΕκΚмΜнΗσΟρΡςϹτΤуΥχΧиΙλΛπΠαΑьΒςϹєΕηΝσΟρΡτΤυΥχΧ";

    static String apply(int style, String s) {
        if (TextUtils.isEmpty(s)) return s;
        switch (style) {
            case 0: return mapAlpha(s, 0x1D4D0, 0x1D4EA, 0x1D7CE);
            case 1: return mapAlpha(s, 0x1D56C, 0x1D586, 0x1D7EC);
            case 2: return doubleStruck(s);
            case 3: return mapAlpha(s, 0x1D5D4, 0x1D5EE, 0x1D7EC);
            case 4: return mapAlpha(s, 0x24B6, 0x24D0, 0);
            case 5: {
                StringBuilder sb = new StringBuilder();
                boolean up = false;
                for (char c : s.toCharArray()) {
                    if (Character.isLetter(c)) {
                        sb.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c));
                        up = !up;
                    } else sb.append(c);
                }
                return sb.toString();
            }
            case 6: {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < s.length(); i++) {
                    sb.append(s.charAt(i));
                    if (i < s.length() - 1 && !Character.isWhitespace(s.charAt(i))) sb.append(' ');
                }
                return sb.toString();
            }
            case 7: return combine(s, '̶');
            case 8: return combine(s, '̲');
            case 9: {
                StringBuilder sb = new StringBuilder();
                for (char c : s.toCharArray()) {
                    char l = Character.toLowerCase(c);
                    sb.append(l >= 'a' && l <= 'z' ? SMALL.charAt(l - 'a') : c);
                }
                return sb.toString();
            }
            case 10: return "★ " + s.trim().replaceAll("\\s+", " ★ ") + " ★";
            case 11: {
                StringBuilder sb = new StringBuilder();
                for (char c : s.toCharArray()) {
                    int k = GREEK_FROM.indexOf(c);
                    sb.append(k >= 0 ? GREEK_TO.charAt(k) : c);
                }
                return sb.toString();
            }
            default: return s;
        }
    }

    /** −1 — выкл. Если включено, все ваши текстовые сообщения уходят этим стилем. */
    public static int autoStyle() { return prefs().getInt("style_auto", -1); }

    static String autoStyleTitle() {
        int a = autoStyle();
        return a < 0 ? "выкл" : STYLES[a].split(" ")[0];
    }

    /** Из StarSend.intercept: всегда писать красиво. */
    static String maybeAuto(String text) {
        int a = autoStyle();
        if (a < 0 || TextUtils.isEmpty(text) || text.startsWith("/") || text.startsWith(".") || text.contains("http")) return text;
        return apply(a, text);
    }

    private static CharSequence[] previews(String sample) {
        CharSequence[] items = new CharSequence[STYLES.length];
        for (int i = 0; i < STYLES.length; i++) items[i] = apply(i, sample) + "   · " + STYLES[i].replaceAll("^[^—]*— ?", "");
        return items;
    }

    /** Меню чата ⋮ → «✨ Красивый шрифт»: переделать текст в поле ввода. */
    public static void fontForInput(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null || chat.getChatActivityEnterView() == null) return;
        CharSequence cur = chat.getChatActivityEnterView().getFieldText();
        if (TextUtils.isEmpty(cur)) {
            chooseAuto(chat, null);
            return;
        }
        String text = cur.toString();
        String sample = text.length() > 18 ? text.substring(0, 18) : text;
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("✨ Красивый шрифт")
                .setItems(previews(sample), (d, which) -> chat.getChatActivityEnterView().setFieldText(apply(which, text)))
                .setNeutralButton("Писать так всегда…", (d, w) -> chooseAuto(chat, null))
                .setNegativeButton("Отмена", null).show();
    }

    static void chooseAuto(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        CharSequence[] base = previews("Привет Hello");
        CharSequence[] items = new CharSequence[base.length + 1];
        items[0] = "🚫 Обычный текст (выключить)";
        System.arraycopy(base, 0, items, 1, base.length);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✨ Всегда писать стилем")
                .setItems(items, (d, which) -> {
                    prefs().edit().putInt("style_auto", which - 1).apply();
                    Toast.makeText(ApplicationLoader.applicationContext, which == 0 ? "Обычный текст" : "✨ Теперь все сообщения — этим стилем", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                }).show();
    }

    // ================================================================ имя и «О себе»

    static final String[][] DECOR = {
            {"꧁ ", " ꧂"}, {"★彡 ", " 彡★"}, {"『", "』"}, {"✦ ", " ✦"}, {"༺ ", " ༻"}, {"˗ˏˋ ", " ˎˊ˗"},
            {"⚡ ", " ⚡"}, {"🔥 ", " 🔥"}, {"♡ ", " ♡"}, {"👑 ", ""}, {"꧁༒☬ ", " ☬༒꧂"}, {"▄︻デ ", " ══━一"},
    };

    static void updateProfile(int account, String first, String about, Runnable ok) {
        TL_account.updateProfile req = new TL_account.updateProfile();
        if (first != null) {
            req.flags |= 1;
            req.first_name = first;
        }
        if (about != null) {
            req.flags |= 4;
            req.about = about;
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null) {
                Toast.makeText(ApplicationLoader.applicationContext, "Telegram не принял: " + err.text, Toast.LENGTH_LONG).show();
                return;
            }
            if (res instanceof TLRPC.User) {
                MessagesController.getInstance(account).putUser((TLRPC.User) res, false);
                UserConfig.getInstance(account).setCurrentUser((TLRPC.User) res);
                UserConfig.getInstance(account).saveConfig(true);
            }
            if (ok != null) ok.run();
        }));
    }

    static void fancyName(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        TLRPC.User me = UserConfig.getInstance(f.getCurrentAccount()).getCurrentUser();
        if (me == null) return;
        String saved = prefs().getString("style_name_orig_" + f.getCurrentAccount(), null);
        final String base = saved != null ? saved : (me.first_name == null ? "" : me.first_name.trim());
        ArrayList<String> variants = new ArrayList<>();
        for (String[] d : DECOR) variants.add(d[0] + base + d[1]);
        for (int s : new int[]{0, 1, 2, 5, 9, 11}) variants.add(apply(s, base));
        CharSequence[] items = new CharSequence[variants.size() + 1];
        for (int i = 0; i < variants.size(); i++) items[i] = variants.get(i);
        items[variants.size()] = "↩️ Вернуть обычное имя «" + base + "»";
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🪪 Красивое имя (видят все)")
                .setItems(items, (d, which) -> {
                    String name = which == variants.size() ? base : variants.get(which);
                    if (name.length() > 64) name = name.substring(0, 64);
                    prefs().edit().putString("style_name_orig_" + f.getCurrentAccount(), base).apply();
                    final String fin = name;
                    updateProfile(f.getCurrentAccount(), fin, null, () ->
                            Toast.makeText(ApplicationLoader.applicationContext, "🪪 Имя теперь: " + fin, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Отмена", null).show();
    }

    static void aiBio(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        if (!StarAi.enabled()) {
            Toast.makeText(f.getParentActivity(), "Нужен ИИ-бот (ai_bot)", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(f.getParentActivity());
        input.setHint("Что про тебя? Например: футбол, аниме, рисую");
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✍️ Крутое «О себе»")
                .setMessage("Нейросеть придумает 5 вариантов — выберешь лучший. До 70 символов.")
                .setView(input)
                .setPositiveButton("Придумать", (d, w) -> {
                    String about = input.getText().toString().trim();
                    String prompt = "Придумай 5 коротких крутых вариантов описания профиля Telegram (поле «О себе») для подростка. "
                            + (about.isEmpty() ? "" : "Интересы: " + about + ". ")
                            + "Каждый не длиннее 65 символов, с 1–2 эмодзи, на русском, дерзко или с юмором, без пошлости и оскорблений. "
                            + "Ответ — 5 строк, без нумерации и пояснений.";
                    Toast.makeText(ApplicationLoader.applicationContext, "✍️ Придумываю…", Toast.LENGTH_SHORT).show();
                    StarAi.ask(f.getCurrentAccount(), prompt, 0, new StarAi.Stream() {
                        @Override
                        public void partial(String text) {
                        }

                        @Override
                        public void done(String text) {
                            AndroidUtilities.runOnUIThread(() -> pickBio(f, text));
                        }

                        @Override
                        public void error(String why) {
                            AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, "ИИ: " + why, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Отмена", null).show();
    }

    private static void pickBio(BaseFragment f, String text) {
        if (f.getParentActivity() == null || text == null) return;
        ArrayList<String> list = new ArrayList<>();
        for (String l : text.split("\n")) {
            String t = l.replaceAll("^[\\s\\d).•*\\-«\"]+", "").replaceAll("[»\"]+$", "").trim();
            if (t.length() >= 3) list.add(t.length() > 70 ? t.substring(0, 70) : t);
            if (list.size() >= 5) break;
        }
        if (list.isEmpty()) return;
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("✍️ Выбери — поставлю в профиль")
                .setItems(list.toArray(new CharSequence[0]), (d, which) -> updateProfile(f.getCurrentAccount(), null, list.get(which), () ->
                        Toast.makeText(ApplicationLoader.applicationContext, "✍️ «О себе» обновлено", Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Закрыть", null).show();
    }

    // ================================================================ красивый скрин

    static final int[][] CARD_COLORS = {
            {0xFF7B5CFF, 0xFF33A6FF}, {0xFFFF5F8F, 0xFFFFB86C}, {0xFF00C9A7, 0xFF3A86FF}, {0xFF111111, 0xFF5B2C83}, {0xFFFF3D00, 0xFFFFC400},
    };

    public static boolean canCard(MessageObject m) {
        return m != null && m.messageOwner != null && !TextUtils.isEmpty(m.messageOwner.message);
    }

    public static void card(ChatActivity chat, MessageObject m) {
        if (chat == null || chat.getParentActivity() == null || !canCard(m)) return;
        String author;
        if (m.isOut()) author = "Я";
        else {
            TLRPC.User u = chat.getMessagesController().getUser(m.getFromChatId());
            if (u != null) author = UserObject.getFirstName(u);
            else {
                TLRPC.Chat c = chat.getMessagesController().getChat(-m.getFromChatId());
                author = c != null && c.title != null ? c.title : "";
            }
        }
        CharSequence[] colors = {"💜 Фиолет", "🍑 Персик", "🌊 Океан", "🖤 Ночь", "🔥 Огонь"};
        final String who = author;
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("📸 Красивый скрин — цвет")
                .setItems(colors, (d, which) -> renderCard(chat.getParentActivity(), who, StarCodec.strip(m.messageOwner.message), CARD_COLORS[which]))
                .show();
    }

    private static TextView tv(Context ctx, String s, int px, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private static void renderCard(Context ctx, String author, String text, int[] colors) {
        try {
            int w = 1080;
            if (text.length() > 600) text = text.substring(0, 600) + "…";
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(90, 140, 90, 120);
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors));
            LinearLayout bubble = new LinearLayout(ctx);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(60, 50, 60, 60);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF2FFFFFF);
            bg.setCornerRadius(60);
            bubble.setBackground(bg);
            if (!TextUtils.isEmpty(author)) {
                TextView a = tv(ctx, author, 44, colors[0], true);
                a.setPadding(0, 0, 0, 18);
                bubble.addView(a);
            }
            bubble.addView(tv(ctx, text, text.length() > 200 ? 46 : 60, 0xFF161616, false));
            root.addView(bubble, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView foot = tv(ctx, "⭐ StarTegs", 36, 0xEEFFFFFF, true);
            foot.setGravity(Gravity.CENTER);
            foot.setPadding(0, 50, 0, 0);
            root.addView(foot);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int h = Math.max(root.getMeasuredHeight(), 1350);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, h);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(b));
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "StarTegs_screen.png");
            try (FileOutputStream os = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            b.recycle();
            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
            ctx.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Поделиться"));
            StarQuest.progress("share", 1);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, "Не удалось сделать картинку", Toast.LENGTH_SHORT).show();
        }
    }
}
