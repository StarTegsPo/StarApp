/*
 * StarTegs 6.0 «Свобода»: раздел «Инструменты» — всё работает на телефоне, без ботов и интернета:
 * QR-код из текста или ссылки, калькулятор, конвертер, генератор паролей, таймер-напоминание,
 * кубик и монетка, счётчик символов. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.graphics.Bitmap;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Random;

public final class StarToolbox {

    private StarToolbox() {}

    interface Done {
        void run(String text);
    }

    private static void input(BaseFragment f, String title, String hint, boolean number, Done done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        EditText in = new EditText(a);
        in.setHint(hint);
        in.setInputType(number ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        FrameLayout box = new FrameLayout(a);
        int p = AndroidUtilities.dp(24);
        box.setPadding(p, 0, p, 0);
        box.addView(in);
        new AlertDialog.Builder(a).setTitle(title).setView(box)
                .setPositiveButton("Готово", (d, w) -> {
                    String t = in.getText().toString().trim();
                    if (!t.isEmpty()) done.run(t);
                }).setNegativeButton("Отмена", null).show();
        in.requestFocus();
    }

    private static void result(BaseFragment f, String title, String text) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        new AlertDialog.Builder(a).setTitle(title).setMessage(text)
                .setPositiveButton("Копировать", (d, w) -> {
                    AndroidUtilities.addToClipboard(text);
                    Toast.makeText(a, "Скопировано", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Закрыть", null).show();
    }

    // ================================================================ QR-код

    public static void qr(BaseFragment f) {
        input(f, "🔳 QR-код", "Текст, ссылка или номер Wi-Fi", false, text -> {
            Activity a = f.getParentActivity();
            if (a == null) return;
            try {
                HashMap<com.google.zxing.EncodeHintType, Object> hints = new HashMap<>();
                hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
                hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
                Bitmap b = new TelegramQRCodeWriter().encode(text, 720, 720, hints, null);
                ImageView iv = new ImageView(a);
                iv.setImageBitmap(b);
                int p = AndroidUtilities.dp(20);
                iv.setPadding(p, p, p, p);
                iv.setBackgroundColor(0xFFFFFFFF);
                new AlertDialog.Builder(a).setTitle("🔳 Твой QR-код").setView(iv)
                        .setPositiveButton("Поделиться", (d, w) -> {
                            try {
                                File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
                                if (!dir.exists()) dir.mkdirs();
                                File file = new File(dir, "StarTegs_QR.png");
                                try (FileOutputStream os = new FileOutputStream(file)) {
                                    b.compress(Bitmap.CompressFormat.PNG, 100, os);
                                }
                                StarVault.shareFile(a, file);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }).setNegativeButton("Закрыть", null).show();
            } catch (Exception e) {
                FileLog.e(e);
                Toast.makeText(a, "Не получилось сделать QR-код", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ================================================================ калькулятор и конвертер

    public static void calc(BaseFragment f) {
        input(f, "🧮 Калькулятор", "Например: (250+150)*1.2 или 15% от 3000 → 3000*0.15", true, text -> {
            try {
                double v = new StarPlugins.Calc(text).parse();
                result(f, "🧮 " + text, "= " + StarPlugins.format(v));
            } catch (Exception e) {
                Toast.makeText(f.getParentActivity(), "Не понял выражение 🙈", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void convert(BaseFragment f) {
        input(f, "🔁 Конвертер", "Например: 5 km mi, 70 kg lb, 2 l gal, 30 c2f", true, text -> {
            String t = text.trim().toLowerCase(java.util.Locale.ROOT);
            String out;
            if (t.endsWith("c2f") || t.endsWith("f2c")) {
                out = StarCmds.run(0, null, t.substring(t.length() - 3), t.substring(0, t.length() - 3).trim());
            } else out = StarCmds2.run(0, null, "conv", t);
            if (out == null || out.startsWith("\u0000")) Toast.makeText(f.getParentActivity(), "Формат: 5 km mi (км, м, см, мили, футы, дюймы, кг, г, фунты, унции, л, мл, галлоны)", Toast.LENGTH_LONG).show();
            else result(f, "🔁 Конвертер", out);
        });
    }

    // ================================================================ пароль

    public static void password(BaseFragment f) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] t = {"12 символов", "16 символов", "24 символа", "Легко запомнить (3 слова)"};
        new AlertDialog.Builder(a).setTitle("🔐 Новый пароль").setItems(t, (d, w) -> {
            SecureRandom r = new SecureRandom();
            String pass;
            if (w == 3) {
                String[] words = {"Kot", "Luna", "Star", "Neon", "Pixel", "Ocean", "Tiger", "Mango", "Rocket", "Frost", "Comet", "Panda"};
                pass = words[r.nextInt(words.length)] + "-" + words[r.nextInt(words.length)] + "-" + words[r.nextInt(words.length)] + r.nextInt(100);
            } else {
                int n = w == 0 ? 12 : w == 1 ? 16 : 24;
                String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*?";
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) b.append(chars.charAt(r.nextInt(chars.length())));
                pass = b.toString();
            }
            result(f, "🔐 Пароль", pass);
        }).show();
    }

    // ================================================================ таймер

    public static void timer(BaseFragment f) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] t = {"5 минут", "10 минут", "25 минут (помидор 🍅)", "1 час", "Своё время…"};
        int[] m = {5, 10, 25, 60, -1};
        new AlertDialog.Builder(a).setTitle("⏰ Напомнить через…").setItems(t, (d, w) -> {
            if (m[w] > 0) startTimer(f, m[w]);
            else input(f, "⏰ Через сколько минут?", "Например: 45", true, s -> {
                try {
                    startTimer(f, Math.max(1, Math.min(60 * 24 * 7, Integer.parseInt(s.trim()))));
                } catch (NumberFormatException e) {
                    Toast.makeText(a, "Нужно число минут", Toast.LENGTH_SHORT).show();
                }
            });
        }).show();
    }

    private static void startTimer(BaseFragment f, int minutes) {
        int when = ConnectionsManager.getInstance(f.getCurrentAccount()).getCurrentTime() + minutes * 60;
        StarLife.remindSelf(f.getCurrentAccount(), "⏰ Время вышло! (таймер на " + minutes + " мин)", when);
        Toast.makeText(f.getParentActivity(), "⏰ Напомню через " + minutes + " мин — сообщение придёт в «Избранное»", Toast.LENGTH_LONG).show();
    }

    // ================================================================ кубик, монетка, число

    public static void random(BaseFragment f) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] t = {"🎲 Бросить кубик", "🪙 Подбросить монетку", "🔢 Число от 1 до 100", "✅ Да или нет"};
        new AlertDialog.Builder(a).setTitle("🎲 Случайность").setItems(t, (d, w) -> {
            Random r = new Random();
            String out = w == 0 ? "🎲 Выпало: " + (1 + r.nextInt(6)) : w == 1 ? (r.nextBoolean() ? "🪙 Орёл" : "🪙 Решка")
                    : w == 2 ? "🔢 " + (1 + r.nextInt(100)) : (r.nextBoolean() ? "✅ Да" : "❌ Нет");
            new AlertDialog.Builder(a).setTitle(t[w]).setMessage(out)
                    .setPositiveButton("Ещё раз", (d2, w2) -> random(f)).setNegativeButton("Закрыть", null).show();
        }).show();
    }

    // ================================================================ счётчик символов

    public static void counter(BaseFragment f) {
        input(f, "🔢 Счётчик символов", "Вставь текст", false, text -> {
            int words = text.trim().isEmpty() ? 0 : text.trim().split("\\s+").length;
            int lines = text.split("\n").length;
            result(f, "🔢 В тексте", "Символов: " + text.length() + "\nБез пробелов: " + text.replaceAll("\\s", "").length()
                    + "\nСлов: " + words + "\nСтрок: " + lines + "\nВ одно сообщение Telegram влезает 4096 символов.");
        });
    }
}
