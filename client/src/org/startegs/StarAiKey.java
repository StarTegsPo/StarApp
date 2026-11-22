/*
 * StarTegs 6.0 «Свобода»: нейросеть без ботов и серверов StarTegs. Пользователь вставляет свой ключ
 * (бесплатный OpenRouter, DeepSeek или любой сервис в формате OpenAI) — приложение обращается к нему
 * напрямую. Ключ хранится только на этом телефоне. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class StarAiKey {

    private StarAiKey() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_ai", Context.MODE_PRIVATE);
    }

    /** {название, адрес, модель по умолчанию, подсказка ключа, сайт}. */
    static final String[][] PROVIDERS = {
            {"OpenRouter — бесплатно", "https://openrouter.ai/api/v1/chat/completions", "auto", "sk-or-", "https://openrouter.ai/keys"},
            {"DeepSeek — дёшево", "https://api.deepseek.com/chat/completions", "deepseek-v4-flash", "sk-", "https://platform.deepseek.com/api_keys"},
            {"Свой сервис (формат OpenAI)", "", "", "", ""},
    };

    static final String SYSTEM = "Ты — Звёздочка, дружелюбный ИИ-помощник в мессенджере StarTegs. Пользователи в основном подростки. "
            + "Отвечай по-русски, коротко, тепло и по делу, без markdown-разметки. Не помогай с опасным и вредным.";

    public static boolean has() {
        return !TextUtils.isEmpty(prefs().getString("key", ""));
    }

    static int provider() { return prefs().getInt("provider", 0); }

    static String url() {
        int p = provider();
        return p < 2 ? PROVIDERS[p][1] : prefs().getString("url", "");
    }

    static String model() {
        String m = prefs().getString("model", "");
        return m.isEmpty() ? PROVIDERS[Math.min(provider(), 1)][2] : m;
    }

    public static String title() {
        return has() ? PROVIDERS[provider()][0].split(" — ")[0] + " · подключена" : "не подключена";
    }

    // ================================================================ настройка

    public static void setup(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] names = new String[PROVIDERS.length + (has() ? 1 : 0)];
        for (int i = 0; i < PROVIDERS.length; i++) names[i] = PROVIDERS[i][0];
        if (has()) names[PROVIDERS.length] = "❌ Отключить нейросеть";
        new AlertDialog.Builder(a).setTitle("🤖 Подключить нейросеть").setItems(names, (d, w) -> {
            if (w == PROVIDERS.length) {
                prefs().edit().clear().apply();
                if (done != null) done.run();
                return;
            }
            askKey(f, w, done);
        }).show();
    }

    private static void askKey(BaseFragment f, int p, Runnable done) {
        Activity a = f.getParentActivity();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        EditText key = new EditText(a);
        key.setHint("Ключ " + (PROVIDERS[p][3].isEmpty() ? "API" : "(начинается с " + PROVIDERS[p][3] + ")"));
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(key);
        EditText url = new EditText(a), model = new EditText(a);
        if (p == 2) {
            url.setHint("Адрес: https://…/v1/chat/completions");
            model.setHint("Модель, например gpt-4o-mini");
            box.addView(url);
            box.addView(model);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(PROVIDERS[p][0]).setView(box)
                .setMessage(p == 0 ? "1. Открой openrouter.ai и войди через Google или почту.\n2. Keys → Create Key.\n3. Скопируй ключ и вставь сюда.\n\nБесплатно: около 50 вопросов в день. Ключ хранится только на этом телефоне."
                        : p == 1 ? "Ключ — на platform.deepseek.com → API keys (нужно пополнить баланс). Ключ хранится только на этом телефоне."
                        : "Подойдёт любой сервис с API в формате OpenAI. Ключ хранится только на этом телефоне.")
                .setPositiveButton("Сохранить", (d, w) -> {
                    String k = key.getText().toString().trim();
                    if (k.isEmpty() || p == 2 && !url.getText().toString().trim().startsWith("https://")) {
                        Toast.makeText(a, "Нужен ключ" + (p == 2 ? " и адрес https://" : ""), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor e = prefs().edit().putString("key", k).putInt("provider", p).remove("free_models");
                    if (p == 2) e.putString("url", url.getText().toString().trim()).putString("model", model.getText().toString().trim());
                    else e.remove("url").remove("model");
                    e.apply();
                    Toast.makeText(a, "🤖 Нейросеть подключена! Попробуй «Звёздочку» или .ai вопрос", Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null);
        if (!PROVIDERS[p][4].isEmpty()) b.setNeutralButton("Получить ключ", (d, w) -> Browser.openUrl(a, PROVIDERS[p][4]));
        b.show();
    }

    // ================================================================ запрос

    /** Бесплатные модели OpenRouter (кэш на сутки). */
    private static JSONArray freeModels() {
        try {
            String cached = prefs().getString("free_models", "");
            if (!cached.isEmpty() && System.currentTimeMillis() - prefs().getLong("free_at", 0) < 86400_000L) return new JSONArray(cached);
            HttpURLConnection c = (HttpURLConnection) new URL("https://openrouter.ai/api/v1/models").openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONArray all = new JSONObject(sb.toString()).optJSONArray("data");
            JSONArray out = new JSONArray();
            String[] pref = {"deepseek", "qwen", "llama", "gemma", "mistral"};
            for (String p : pref) {
                for (int i = 0; all != null && i < all.length() && out.length() < 3; i++) {
                    String id = all.optJSONObject(i).optString("id");
                    if (id.endsWith(":free") && id.contains(p)) out.put(id);
                }
            }
            for (int i = 0; all != null && i < all.length() && out.length() < 3; i++) {
                String id = all.optJSONObject(i).optString("id");
                if (id.endsWith(":free")) out.put(id);
            }
            prefs().edit().putString("free_models", out.toString()).putLong("free_at", System.currentTimeMillis()).apply();
            return out;
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    static void ask(String prompt, StarAi.Stream cb) {
        String key = prefs().getString("key", "");
        new Thread(() -> { // 6.1: отдельный поток — длинный ответ не тормозит очередь Telegram
            HttpURLConnection c = null;
            try {
                JSONObject body = new JSONObject();
                String model = model();
                if (provider() == 0 && "auto".equals(model)) {
                    JSONArray free = freeModels();
                    if (free.length() == 0) throw new Exception("нет бесплатных моделей");
                    body.put("model", free.optString(0));
                    if (free.length() > 1) body.put("models", free);
                } else body.put("model", model);
                String p = prompt.length() > 12000 ? prompt.substring(prompt.length() - 12000) : prompt;
                body.put("stream", true).put("max_tokens", 3000).put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", SYSTEM))
                        .put(new JSONObject().put("role", "user").put("content", p)));
                c = (HttpURLConnection) new URL(url()).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(120000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("X-Title", "StarTegs");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
                int code = c.getResponseCode();
                if (code != 200) {
                    String why = code == 401 ? "Неверный ключ нейросети — проверь его в настройках"
                            : code == 402 ? "На счёте закончились деньги или бесплатный лимит"
                            : code == 429 ? "Слишком много вопросов — подожди минуту (или лимит на сегодня закончился)"
                            : "Нейросеть ответила ошибкой " + code;
                    AndroidUtilities.runOnUIThread(() -> cb.error(why));
                    return;
                }
                StringBuilder text = new StringBuilder();
                long lastPush = 0;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        if (data.equals("[DONE]")) break;
                        try {
                            JSONObject ch = new JSONObject(data).optJSONArray("choices").optJSONObject(0);
                            String piece = ch.optJSONObject("delta") != null ? ch.optJSONObject("delta").optString("content", "") : "";
                            if (!piece.isEmpty() && !"null".equals(piece)) text.append(piece);
                        } catch (Exception ignore) {
                        }
                        if (System.currentTimeMillis() - lastPush > 250) {
                            lastPush = System.currentTimeMillis();
                            String part = StarAi.plain(text.toString());
                            AndroidUtilities.runOnUIThread(() -> cb.partial(part));
                        }
                    }
                }
                String fin = StarAi.plain(text.toString());
                AndroidUtilities.runOnUIThread(() -> {
                    if (fin.isEmpty()) cb.error("Нейросеть промолчала — попробуй ещё раз");
                    else cb.done(fin);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> cb.error("Нет связи с нейросетью — проверь интернет"));
            } finally {
                if (c != null) c.disconnect();
            }
        }, "StarAiKey").start();
    }
}
