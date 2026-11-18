/*
 * StarTegs 4.0 — «Плагины»: команды прямо в поле ввода. Пишете «.calc 2+2*2» — уходит «2+2*2 = 6».
 * Встроенные команды (.help, .calc, .tr, .ai, .time, .rand, .flip, .shrug, .rev, .up, .mock, .id)
 * и свои команды-макросы: «.адрес» → ваш текст. Работают только в ваших исходящих сообщениях.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class StarPlugins {

    private StarPlugins() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static boolean enabled() { return prefs().getBoolean("plugins_on", true); }

    static final String HELP = ".help — список команд\n"
            + ".calc 2+2*2 — калькулятор\n"
            + ".tr en Привет — перевести и отправить\n"
            + ".ai вопрос — ответ нейросети\n"
            + ".time — текущее время\n"
            + ".rand 1 100 — случайное число\n"
            + ".flip — орёл или решка\n"
            + ".shrug текст — ¯\\_(ツ)_/¯\n"
            + ".rev текст — задом наперёд\n"
            + ".fix ghbdtn — исправить раскладку\n"
            + ".choose пицца, суши, бургер — выбрать за тебя\n"
            + ".ball вопрос — шар судьбы\n"
            + ".love Маша Петя — совместимость\n"
            + ".up текст — БОЛЬШИМИ БУКВАМИ\n"
            + ".mock текст — ТеКсТ ВоТ ТаК\n"
            + ".id — ID этого чата\n"
            + "/название_шаблона — вставить шаблон ответа\n"
            + "Свои команды — в StarTegs → Новое в 4.0 → Мои команды.";

    // ---------------------------------------------------------------- свои команды

    /** Строки вида «имя=текст». */
    static Map<String, String> macros() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        String raw = prefs().getString("plugins_macros", "");
        for (String line : raw.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (k.startsWith(".")) k = k.substring(1);
            String v = line.substring(eq + 1).trim().replace("\\n", "\n");
            if (!k.isEmpty() && !v.isEmpty()) m.put(k, v);
        }
        return m;
    }

    static int macroCount() {
        return macros().size();
    }

    static void editMacros(BaseFragment f, Runnable done) {
        if (f == null || f.getParentActivity() == null) return;
        EditText input = new EditText(f.getParentActivity());
        input.setText(prefs().getString("plugins_macros", ""));
        input.setHint("адрес=ул. Ленина, 1\nкарта=2200 0000 0000 0000");
        input.setMinLines(4);
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🧩 Мои команды")
                .setMessage("Каждая строка — «имя=текст». В чате пишете «.имя» — уходит текст. \\n — перенос строки.")
                .setView(input)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs().edit().putString("plugins_macros", input.getText().toString()).apply();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null).show();
    }

    // ---------------------------------------------------------------- обработка

    private static void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_SHORT).show();
    }

    private static BaseFragment top() {
        try {
            return LaunchActivity.getLastFragment();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sendText(int account, SendMessagesHelper.SendMessageParams p, String text) {
        SendMessagesHelper.SendMessageParams t = SendMessagesHelper.SendMessageParams.of(text, p.peer, p.replyToMsg, p.replyToTopMsg, null, true, null, null, null, true, 0, 0, null, false);
        StarSend.direct(account, t);
    }

    /**
     * Вызывается в начале StarSend.intercept. true — сообщение забрали (отправим сами или
     * не отправляем вовсе); false — отправка идёт дальше (возможно, с изменённым p.message).
     */
    static boolean handle(int account, SendMessagesHelper.SendMessageParams p) {
        try {
            if (!enabled() || p == null || p.message == null) return false;
            String msg = p.message.trim();
            if (msg.length() >= 2 && msg.charAt(0) == '/' && template(account, p, msg.substring(1))) return false;
            if (msg.length() < 2 || msg.charAt(0) != '.' || !Character.isLetter(msg.charAt(1))) return false;
            int sp = indexOfSpace(msg);
            String cmd = (sp < 0 ? msg.substring(1) : msg.substring(1, sp)).toLowerCase(Locale.ROOT);
            String arg = sp < 0 ? "" : msg.substring(sp + 1).trim();
            String out = null;
            switch (cmd) {
                case "help":
                case "помощь": {
                    BaseFragment f = top();
                    if (f != null && f.getParentActivity() != null) {
                        new AlertDialog.Builder(f.getParentActivity()).setTitle("🧩 Команды StarTegs").setMessage(HELP).setPositiveButton("OK", null).show();
                    }
                    return true;
                }
                case "id": {
                    BaseFragment f = top();
                    String text = "ID чата: " + p.peer;
                    if (f != null && f.getParentActivity() != null) {
                        new AlertDialog.Builder(f.getParentActivity()).setTitle("🆔 ID").setMessage(text)
                                .setPositiveButton("Копировать", (d, w) -> AndroidUtilities.addToClipboard(String.valueOf(p.peer)))
                                .setNegativeButton("Закрыть", null).show();
                    }
                    return true;
                }
                case "calc":
                case "c": {
                    if (arg.isEmpty()) return false;
                    try {
                        out = arg + " = " + format(new Calc(arg).parse());
                    } catch (Exception e) {
                        toast("🧮 Не понял выражение");
                        return true;
                    }
                    break;
                }
                case "time":
                    out = "🕒 " + new SimpleDateFormat("HH:mm, d MMMM yyyy", new Locale("ru")).format(new Date());
                    break;
                case "rand":
                case "dice": {
                    long a = 1, b = 100;
                    String[] parts = arg.isEmpty() ? new String[0] : arg.split("\\s+");
                    try {
                        if (parts.length == 1) b = Long.parseLong(parts[0]);
                        else if (parts.length >= 2) { a = Long.parseLong(parts[0]); b = Long.parseLong(parts[1]); }
                    } catch (Exception ignore) {}
                    if (b < a) { long x = a; a = b; b = x; }
                    long span = b - a + 1;
                    long r = a + (span <= 0 ? 0 : (long) (new Random().nextDouble() * span));
                    out = "🎲 " + r + " (от " + a + " до " + b + ")";
                    break;
                }
                case "flip":
                    out = new Random().nextBoolean() ? "🪙 Орёл" : "🪙 Решка";
                    break;
                case "shrug":
                    out = (arg.isEmpty() ? "" : arg + " ") + "¯\\_(ツ)_/¯";
                    break;
                case "fix": // 4.7: ghbdtn → привет
                    if (arg.isEmpty()) return false;
                    out = StarX.fixLayout(arg);
                    break;
                case "choose": {
                    String[] v = arg.split("\\s*(,|\\sили\\s)\\s*");
                    if (v.length < 2) return false;
                    out = "🎯 Выбираю: " + v[new java.util.Random().nextInt(v.length)].trim();
                    break;
                }
                case "ball": {
                    String[] a = {"Да ✅", "Нет ❌", "Скорее да 🙂", "Скорее нет 🙃", "Без сомнений 💯", "Спроси позже ⏳", "Даже не думай 🙅", "Звёзды говорят «да» ✨"};
                    out = (arg.isEmpty() ? "" : "❓ " + arg + "\n") + "🎱 " + a[new java.util.Random().nextInt(a.length)];
                    break;
                }
                case "love": {
                    String[] v = arg.split("\\s*(\\+|,|\\sи\\s)\\s*|\\s+");
                    if (v.length < 2) return false;
                    String x = v[0].toLowerCase(Locale.ROOT), y = v[1].toLowerCase(Locale.ROOT);
                    String key = x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
                    int pct = Math.abs(key.hashCode() % 101);
                    out = "💞 " + v[0] + " + " + v[1] + " = " + pct + "% " + (pct > 80 ? "🔥" : pct > 50 ? "💖" : pct > 25 ? "🙂" : "🥶");
                    break;
                }
                case "rev":
                    if (arg.isEmpty()) return false;
                    out = new StringBuilder(arg).reverse().toString();
                    break;
                case "up":
                    if (arg.isEmpty()) return false;
                    out = arg.toUpperCase(Locale.ROOT);
                    break;
                case "mock": {
                    if (arg.isEmpty()) return false;
                    StringBuilder sb = new StringBuilder();
                    boolean upper = false;
                    for (char ch : arg.toCharArray()) {
                        if (Character.isLetter(ch)) {
                            sb.append(upper ? Character.toUpperCase(ch) : Character.toLowerCase(ch));
                            upper = !upper;
                        } else sb.append(ch);
                    }
                    out = sb.toString();
                    break;
                }
                case "tr": {
                    int s2 = indexOfSpace(arg);
                    if (s2 <= 0) {
                        toast("Формат: .tr en текст");
                        return true;
                    }
                    String lang = arg.substring(0, s2).toLowerCase(Locale.ROOT);
                    String text = arg.substring(s2 + 1).trim();
                    toast("🌍 Перевожу…");
                    StarPlus.translate(account, text, lang, (res, err) -> {
                        if (res == null) {
                            toast("Ошибка перевода: " + err);
                            return;
                        }
                        sendText(account, p, res);
                    });
                    return true;
                }
                case "ai": {
                    if (arg.isEmpty() || !StarAi.enabled()) return false;
                    toast("🤖 Думаю…");
                    StarAi.ask(account, arg + "\n\nОтветь кратко, без markdown.", 0, new StarAi.Stream() {
                        @Override
                        public void partial(String text) {
                        }

                        @Override
                        public void done(String text) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (TextUtils.isEmpty(text)) {
                                    toast("ИИ не ответил");
                                    return;
                                }
                                String t = text.trim();
                                if (t.length() > 4000) t = t.substring(0, 4000);
                                sendText(account, p, "🤖 " + t);
                            });
                        }

                        @Override
                        public void error(String why) {
                            AndroidUtilities.runOnUIThread(() -> toast("ИИ: " + why));
                        }
                    });
                    return true;
                }
                default: {
                    String macro = macros().get(cmd);
                    if (macro == null) return false;
                    out = arg.isEmpty() ? macro : macro + " " + arg;
                }
            }
            if (out == null) return false;
            StarAchieve.bump("plugin", 1);
            p.message = out;
            p.entities = null;
            return false;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    /** «/реквизиты» → текст шаблона «Реквизиты» ({имя} — имя собеседника). Команды ботов не трогаем: только точное совпадение с шаблоном. */
    private static boolean template(int account, SendMessagesHelper.SendMessageParams p, String name) {
        String want = name.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (want.isEmpty() || want.contains("@")) return false;
        org.json.JSONArray a = StarMore.templates();
        for (int i = 0; i < a.length(); i++) {
            org.json.JSONObject t = a.optJSONObject(i);
            if (t == null || !want.equals(t.optString("t").trim().toLowerCase(Locale.ROOT))) continue;
            String who = "";
            if (p.peer > 0) {
                org.telegram.tgnet.TLRPC.User u = org.telegram.messenger.MessagesController.getInstance(account).getUser(p.peer);
                if (u != null && u.bot) return false; // в чате с ботом «/…» — это команда бота
                if (u != null) who = org.telegram.messenger.UserObject.getFirstName(u);
            }
            p.message = t.optString("x").replace("{имя}", who).replace("{name}", who);
            p.entities = null;
            return true;
        }
        return false;
    }

    private static int indexOfSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "∞";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.valueOf((long) v);
        String s = String.format(Locale.US, "%.10f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** Калькулятор: + − × ÷ ^ %, скобки, дроби с точкой или запятой. */
    static final class Calc {
        private final String s;
        private int pos = -1, ch;

        Calc(String expr) {
            s = expr.replace(',', '.').replace('×', '*').replace('÷', '/').replace('−', '-').replace(':', '/').replace('х', '*').replace('x', '*');
        }

        private void next() {
            ch = ++pos < s.length() ? s.charAt(pos) : -1;
        }

        private boolean eat(int c) {
            while (ch == ' ') next();
            if (ch == c) {
                next();
                return true;
            }
            return false;
        }

        double parse() {
            next();
            double x = expr();
            while (ch == ' ') next();
            if (pos < s.length()) throw new IllegalArgumentException("лишнее: " + (char) ch);
            return x;
        }

        private double expr() {
            double x = term();
            for (;;) {
                if (eat('+')) x += term();
                else if (eat('-')) x -= term();
                else return x;
            }
        }

        private double term() {
            double x = factor();
            for (;;) {
                if (eat('*')) x *= factor();
                else if (eat('/')) x /= factor();
                else if (eat('%')) x = x / 100.0;
                else return x;
            }
        }

        private double factor() {
            if (eat('+')) return factor();
            if (eat('-')) return -factor();
            double x;
            if (eat('(')) {
                x = expr();
                if (!eat(')')) throw new IllegalArgumentException("нет )");
            } else if (ch >= '0' && ch <= '9' || ch == '.') {
                int start = pos;
                while (ch >= '0' && ch <= '9' || ch == '.') next();
                x = Double.parseDouble(s.substring(start, pos));
            } else {
                throw new IllegalArgumentException("неожиданно: " + (char) ch);
            }
            if (eat('^')) x = Math.pow(x, factor());
            return x;
        }
    }
}
