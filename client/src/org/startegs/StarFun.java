/*
 * StarTegs 4.3 — развлечения и кошелёк:
 *   • гороскоп дня от нейросети (подробный — Premium) — №59;
 *   • питомец, который растёт от вашей активности в Telegram — №63;
 *   • колесо бонусов раз в день, призы — только косметика (бот v62) — №70;
 *   • кошелёк 💎: перевод кристаллов другу и чаевые авторам тем (бот v62) — №68;
 *   • значок «Поддержал StarTegs» за донат звёздами (бот v62) — №72.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class StarFun {

    private StarFun() {}

    public static final int MENU_SEND_CRYSTALS = 9343;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    // ================================================================ гороскоп

    static final String[] SIGNS = {"♈ Овен", "♉ Телец", "♊ Близнецы", "♋ Рак", "♌ Лев", "♍ Дева",
            "♎ Весы", "♏ Скорпион", "♐ Стрелец", "♑ Козерог", "♒ Водолей", "♓ Рыбы"};

    static int sign() { return prefs().getInt("fun_sign", -1); }

    static void horoscope(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        if (sign() < 0) {
            chooseSign(f, () -> horoscope(f));
            return;
        }
        final boolean full = StarMod.isPro();
        String key = "fun_horo_" + today() + "_" + sign() + (full ? "_full" : "");
        String cached = prefs().getString(key, null);
        if (cached != null) {
            showHoroscope(f, cached, full);
            return;
        }
        if (!StarAi.enabled()) {
            Toast.makeText(f.getParentActivity(), "Нужен ИИ-бот (ai_bot)", Toast.LENGTH_SHORT).show();
            return;
        }
        String s = SIGNS[sign()].substring(2);
        String prompt = "Составь добрый шуточный гороскоп на " + new SimpleDateFormat("d MMMM yyyy", new Locale("ru")).format(new Date()) + " для знака " + s + ". "
                + (full ? "Разделы: общее, любовь, работа, деньги, совет дня, счастливое число. 8–10 предложений."
                : "2–3 предложения и счастливое число.")
                + " Без markdown, с парой эмодзи. Это развлечение, не прогноз.";
        Toast.makeText(f.getParentActivity(), "🔮 Звёзды думают…", Toast.LENGTH_SHORT).show();
        StarAi.ask(f.getCurrentAccount(), prompt, 0, new StarAi.Stream() {
            @Override
            public void partial(String text) {
            }

            @Override
            public void done(String text) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (text == null || text.trim().isEmpty()) return;
                    prefs().edit().putString(key, text.trim()).apply();
                    showHoroscope(f, text.trim(), full);
                });
            }

            @Override
            public void error(String why) {
                AndroidUtilities.runOnUIThread(() -> Toast.makeText(ApplicationLoader.applicationContext, "ИИ: " + why, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void showHoroscope(BaseFragment f, String text, boolean full) {
        if (f.getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🔮 " + SIGNS[sign()] + " · сегодня")
                .setMessage(text + (full ? "" : "\n\n⭐ Подробный гороскоп (любовь, работа, деньги) — в Premium."))
                .setPositiveButton("OK", null)
                .setNeutralButton("Сменить знак", (d, w) -> chooseSign(f, () -> horoscope(f)));
        b.show();
    }

    static void chooseSign(BaseFragment f, Runnable then) {
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("Ваш знак зодиака")
                .setItems(SIGNS, (d, which) -> {
                    prefs().edit().putInt("fun_sign", which).apply();
                    if (then != null) then.run();
                }).show();
    }

    // ================================================================ питомец

    static final String[][] PETS = {
            {"🐱", "Котик"}, {"🐶", "Пёсик"}, {"🦊", "Лисёнок"}, {"🐼", "Панда"}, {"🐉", "Дракончик"}, {"🦄", "Единорог"},
    };
    private static final String[] STAGES = {"🥚 Яйцо", "🐣 Малыш", "Подросток", "Взрослый", "👑 Легенда"};
    private static final int[] STAGE_XP = {0, 50, 300, 1200, 5000};

    static int petKind() { return prefs().getInt("pet_kind", -1); }

    static int petXp() {
        int fromChat = StarAchieve.get("sent") + StarAchieve.get("games") * 5 + StarAchieve.get("quest") * 20;
        return prefs().getInt("pet_xp", 0) + fromChat * (StarMod.isPro() ? 2 : 1);
    }

    static int petStage() {
        int xp = petXp(), s = 0;
        for (int i = 0; i < STAGE_XP.length; i++) if (xp >= STAGE_XP[i]) s = i;
        return s;
    }

    static String petTitle() {
        if (petKind() < 0) return "завести";
        int st = petStage();
        String face = st == 0 ? "🥚" : st == 1 ? "🐣" : PETS[petKind()][0];
        return face + " " + prefs().getString("pet_name", PETS[petKind()][1]) + " · " + STAGES[st].replaceAll("^[^ ]+ ", "");
    }

    private static boolean hungry() {
        return System.currentTimeMillis() - prefs().getLong("pet_fed", 0) > 24 * 3600_000L;
    }

    static void pet(BaseFragment f, Runnable refresh) {
        if (f == null || f.getParentActivity() == null) return;
        if (petKind() < 0) {
            CharSequence[] items = new CharSequence[PETS.length];
            for (int i = 0; i < PETS.length; i++) items[i] = PETS[i][0] + "  " + PETS[i][1];
            new AlertDialog.Builder(f.getParentActivity())
                    .setTitle("🥚 Выберите питомца")
                    .setItems(items, (d, which) -> {
                        prefs().edit().putInt("pet_kind", which).putLong("pet_fed", System.currentTimeMillis()).apply();
                        if (refresh != null) refresh.run();
                        pet(f, refresh);
                    }).show();
            return;
        }
        int xp = petXp(), st = petStage();
        int next = st + 1 < STAGE_XP.length ? STAGE_XP[st + 1] : -1;
        String mood = hungry() ? "😢 Скучает и хочет есть" : "😊 Сыт и доволен";
        String text = petTitle() + "\n\nОпыт: " + xp + (next > 0 ? " / " + next + " до стадии «" + STAGES[st + 1] + "»" : " — максимальная стадия!")
                + "\nНастроение: " + mood
                + "\n\nПитомец растёт, когда вы пишете сообщения, играете и выполняете задания дня. Кормить можно раз в 4 часа (+15 опыта)."
                + (StarMod.isPro() ? "\n⭐ Premium: опыт ×2" : "\n⭐ С Premium опыт растёт вдвое быстрее");
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🐾 Мой питомец")
                .setMessage(text)
                .setPositiveButton("🍖 Покормить", (d, w) -> {
                    long last = prefs().getLong("pet_fed", 0);
                    if (System.currentTimeMillis() - last < 4 * 3600_000L) {
                        Toast.makeText(ApplicationLoader.applicationContext, "Ещё сыт 🙂 Приходите позже", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs().edit().putLong("pet_fed", System.currentTimeMillis()).putInt("pet_xp", prefs().getInt("pet_xp", 0) + 15).apply();
                    Toast.makeText(ApplicationLoader.applicationContext, "😋 Ням! +15 опыта", Toast.LENGTH_SHORT).show();
                    if (refresh != null) refresh.run();
                })
                .setNeutralButton("Имя", (d, w) -> {
                    EditText input = new EditText(f.getParentActivity());
                    input.setText(prefs().getString("pet_name", PETS[petKind()][1]));
                    new AlertDialog.Builder(f.getParentActivity()).setTitle("Имя питомца").setView(input)
                            .setPositiveButton("OK", (d2, w2) -> {
                                String n = input.getText().toString().trim();
                                if (!n.isEmpty()) prefs().edit().putString("pet_name", n.length() > 20 ? n.substring(0, 20) : n).apply();
                                if (refresh != null) refresh.run();
                            }).show();
                })
                .setNegativeButton("Закрыть", null).show();
    }

    // ================================================================ колесо бонусов (бот v62)

    static void spin(BaseFragment f, Runnable refresh) {
        if (f == null || f.getParentActivity() == null) return;
        Toast.makeText(f.getParentActivity(), "🎡 Крутим…", Toast.LENGTH_SHORT).show();
        StarCloud.call(f.getCurrentAccount(), "wheel_spin", new JSONObject(), new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                if (f.getParentActivity() == null) return;
                new AlertDialog.Builder(f.getParentActivity())
                        .setTitle("🎉 Выигрыш!")
                        .setMessage(res.optString("prize", "Приз") + "\n\nПриз уже в магазине StarTegs — его можно надеть. Следующая попытка — завтра.")
                        .setPositiveButton("Ура", null).show();
                if (refresh != null) refresh.run();
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================================================================ кошелёк (бот v62)

    public static boolean canSend(ChatActivity chat) {
        if (chat == null || chat.getDialogId() <= 0 || StarModConfig.ECO_BOT.isEmpty()) return false;
        TLRPC.User u = chat.getMessagesController().getUser(chat.getDialogId());
        return u != null && !u.bot && !u.self;
    }

    public static void sendCrystals(ChatActivity chat) {
        if (!canSend(chat) || chat.getParentActivity() == null) return;
        TLRPC.User u = chat.getMessagesController().getUser(chat.getDialogId());
        EditText input = new EditText(chat.getParentActivity());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Сколько 💎");
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("💎 Перевести " + UserObject.getFirstName(u))
                .setMessage("Кристаллы уйдут из вашего баланса в боте @" + StarModConfig.ECO_BOT + ". Получателю нужно хотя бы раз нажать /start в боте.")
                .setView(input)
                .setPositiveButton("Перевести", (d, w) -> {
                    int amount;
                    try {
                        amount = Integer.parseInt(input.getText().toString().trim());
                    } catch (Exception e) {
                        return;
                    }
                    if (amount <= 0) return;
                    try {
                        StarCloud.call(chat.getCurrentAccount(), "wallet_send", new JSONObject().put("to", u.id).put("amount", amount), new StarCloud.Result() {
                            @Override
                            public void ok(JSONObject res) {
                                Toast.makeText(ApplicationLoader.applicationContext, "💎 Переведено " + amount + ". Осталось: " + res.optInt("crystals"), Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void fail(String why) {
                                Toast.makeText(ApplicationLoader.applicationContext, why, Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception ignore) {
                    }
                })
                .setNegativeButton("Отмена", null).show();
    }

    static void tipTheme(BaseFragment f, long themeId) {
        if (f == null || f.getParentActivity() == null) return;
        CharSequence[] items = {"💎 1", "💎 5", "💎 10", "💎 25"};
        int[] amounts = {1, 5, 10, 25};
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("Чаевые автору темы")
                .setItems(items, (d, which) -> {
                    try {
                        StarCloud.call(f.getCurrentAccount(), "theme_tip", new JSONObject().put("id", themeId).put("amount", amounts[which]), new StarCloud.Result() {
                            @Override
                            public void ok(JSONObject res) {
                                Toast.makeText(ApplicationLoader.applicationContext, "💖 Спасибо! Автор получил " + amounts[which] + " 💎", Toast.LENGTH_SHORT).show();
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

    static void balance(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        StarCloud.call(f.getCurrentAccount(), "cos_state", null, new StarCloud.Result() {
            @Override
            public void ok(JSONObject res) {
                if (f.getParentActivity() == null) return;
                new AlertDialog.Builder(f.getParentActivity())
                        .setTitle("💰 Кошелёк")
                        .setMessage("Баланс: " + res.optInt("crystals") + " 💎\n\nПеревести другу: откройте с ним чат → ⋮ → «💎 Перевести кристаллы». "
                                + "Чаевые автору темы: магазин → Темы от пользователей → купленная тема.")
                        .setPositiveButton("Пополнить", (d, w) -> Browser.openUrl(f.getParentActivity(), "https://t.me/" + StarModConfig.ECO_BOT + "?start=crystals"))
                        .setNegativeButton("Закрыть", null).show();
            }

            @Override
            public void fail(String why) {
                if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), why, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ================================================================ поддержать (бот v62)

    static void donate(BaseFragment f) {
        if (f == null || f.getParentActivity() == null) return;
        CharSequence[] items = {"⭐ 50", "⭐ 100", "⭐ 250", "⭐ 500"};
        int[] stars = {50, 100, 250, 500};
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("🫶 Поддержать StarTegs")
                .setMessage("За любой донат — значок «🫶 Поддержал StarTegs» у имени навсегда (видят друзья с модом).")
                .setItems(items, (d, which) -> {
                    try {
                        StarCloud.call(f.getCurrentAccount(), "donate_link", new JSONObject().put("stars", stars[which]), new StarCloud.Result() {
                            @Override
                            public void ok(JSONObject res) {
                                String link = res.optString("link");
                                if (!link.isEmpty() && f.getParentActivity() != null) Browser.openUrl(f.getParentActivity(), link);
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
}
