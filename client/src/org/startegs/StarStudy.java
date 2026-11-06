/*
 * StarTegs 3.0 — «Учёба»: расписание уроков, домашка с напоминаниями и ИИ-репетитор,
 * который объясняет решение по шагам, а не просто даёт ответ. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StarStudy extends UniversalFragment {

    private static final String[] DAYS = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"};
    private static final int ID_TUTOR = 100;
    private static final int ID_HW_ADD = 101;
    private static final int ID_DAY = 200;
    private static final int ID_HW = 1000;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_notes", Context.MODE_PRIVATE);
    }

    private static JSONArray homework() {
        try {
            return new JSONArray(prefs().getString("homework", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return "📚 Учёба";
    }

    private static int todayIndex() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // 1 — воскресенье
        return d == Calendar.SUNDAY ? 0 : d - 2; // в воскресенье показываем понедельник
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (StarAi.enabled()) {
            items.add(UItem.asButton(ID_TUTOR, R.drawable.msg_bot, "🧑‍🏫 ИИ-репетитор: объясни задачу").accent());
            items.add(UItem.asShadow("Вставь условие задачи — ИИ объяснит решение по шагам и проверит, понял ли ты. Готовые ответы без объяснения он не даёт 😉"));
        }
        int today = todayIndex();
        items.add(UItem.asHeader("Расписание"));
        for (int i = 0; i < DAYS.length; i++) {
            String lessons = prefs().getString("lessons_" + i, "");
            String preview = lessons.isEmpty() ? "добавить" : lessons.replace('\n', ',').replace(",", ", ");
            if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
            items.add(UItem.asButton(ID_DAY + i, (i == today ? "👉 " : "") + DAYS[i], preview));
        }
        items.add(UItem.asHeader("Домашка"));
        items.add(UItem.asButton(ID_HW_ADD, R.drawable.msg_edit, "➕ Добавить задание"));
        JSONArray hw = homework();
        SimpleDateFormat f = new SimpleDateFormat("EE, d MMM", new Locale("ru"));
        long now = System.currentTimeMillis();
        for (int i = 0; i < hw.length(); i++) {
            JSONObject o = hw.optJSONObject(i);
            if (o == null) continue;
            long due = o.optLong("due");
            String when = due == 0 ? "" : (due < now ? "⚠️ " : "") + f.format(new Date(due));
            items.add(UItem.asButton(ID_HW + i, (o.optBoolean("done") ? "✅ " : "📝 ") + o.optString("t"), when));
        }
        items.add(UItem.asShadow("Нажми на задание — отметить сделанным или удалить. Накануне вечером в 19:00 придёт напоминание в «Избранное»."));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        if (item.id == ID_TUTOR) {
            tutor(ctx);
            return;
        }
        if (item.id >= ID_DAY && item.id < ID_DAY + DAYS.length) {
            int d = item.id - ID_DAY;
            EditText input = new EditText(ctx);
            input.setMinLines(5);
            input.setHint("Каждый урок с новой строки");
            input.setText(prefs().getString("lessons_" + d, ""));
            LinearLayout box = new LinearLayout(ctx);
            box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
            box.addView(input, new LinearLayout.LayoutParams(-1, -2));
            new AlertDialog.Builder(ctx)
                    .setTitle(DAYS[d])
                    .setView(box)
                    .setPositiveButton("Сохранить", (dd, w) -> {
                        prefs().edit().putString("lessons_" + d, input.getText().toString().trim()).apply();
                        refresh();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }
        if (item.id == ID_HW_ADD) {
            addHomework(ctx);
            return;
        }
        int i = item.id - ID_HW;
        JSONArray hw = homework();
        if (i < 0 || i >= hw.length()) return;
        JSONObject o = hw.optJSONObject(i);
        new AlertDialog.Builder(ctx)
                .setTitle(o.optString("t"))
                .setPositiveButton(o.optBoolean("done") ? "Не сделано" : "✅ Сделано", (d, w) -> {
                    try {
                        o.put("done", !o.optBoolean("done"));
                        hw.put(i, o);
                        prefs().edit().putString("homework", hw.toString()).apply();
                        if (o.optBoolean("done")) {
                            StarFx.play(org.telegram.ui.LaunchActivity.getLastFragment(), "fx_confetti");
                            StarAchieve.bump("homework", 1);
                            StarQuest.progress("homework", 1);
                        }
                    } catch (Exception ignore) {
                    }
                    refresh();
                })
                .setNegativeButton("Удалить", (d, w) -> {
                    JSONArray out = new JSONArray();
                    for (int k = 0; k < hw.length(); k++) if (k != i) out.put(hw.opt(k));
                    prefs().edit().putString("homework", out.toString()).apply();
                    refresh();
                })
                .show();
    }

    private void refresh() {
        if (listView != null) listView.adapter.update(true);
    }

    private void addHomework(Context ctx) {
        EditText input = new EditText(ctx);
        input.setHint("Например: алгебра — №245, 247");
        LinearLayout box = new LinearLayout(ctx);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        CharSequence[] when = {"На завтра", "Через 2 дня", "Через неделю", "Без срока"};
        new AlertDialog.Builder(ctx)
                .setTitle("📝 Новое задание")
                .setView(box)
                .setPositiveButton("Дальше", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) return;
                    new AlertDialog.Builder(ctx)
                            .setTitle("Когда сдавать?")
                            .setItems(when, (d2, which) -> {
                                long due = 0;
                                if (which < 3) {
                                    Calendar c = Calendar.getInstance();
                                    c.add(Calendar.DAY_OF_YEAR, which == 0 ? 1 : which == 1 ? 2 : 7);
                                    c.set(Calendar.HOUR_OF_DAY, 8);
                                    c.set(Calendar.MINUTE, 0);
                                    due = c.getTimeInMillis();
                                    remind(t, due);
                                }
                                try {
                                    JSONArray hw = homework();
                                    hw.put(new JSONObject().put("t", t.length() > 200 ? t.substring(0, 200) : t).put("due", due).put("done", false));
                                    prefs().edit().putString("homework", hw.toString()).apply();
                                } catch (Exception ignore) {
                                }
                                refresh();
                            })
                            .show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    /** Напоминание накануне в 19:00 — отложенным сообщением в «Избранное». */
    private void remind(String task, long due) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(due);
        c.add(Calendar.DAY_OF_YEAR, -1);
        c.set(Calendar.HOUR_OF_DAY, 19);
        c.set(Calendar.MINUTE, 0);
        int when = (int) (c.getTimeInMillis() / 1000);
        int now = getConnectionsManager().getCurrentTime();
        if (when < now + 120) when = now + 3600;
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        SendMessagesHelper.SendMessageParams p = SendMessagesHelper.SendMessageParams.of("📚 Напоминание StarTegs: завтра сдавать —\n" + task, self);
        p.scheduleDate = when;
        SendMessagesHelper.getInstance(currentAccount).sendMessage(p);
        Toast.makeText(getParentActivity(), "⏰ Напомню накануне вечером", Toast.LENGTH_SHORT).show();
    }

    private void tutor(Context ctx) {
        EditText input = new EditText(ctx);
        input.setMinLines(4);
        input.setHint("Вставь условие задачи или вопрос по теме");
        LinearLayout box = new LinearLayout(ctx);
        box.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        CharSequence[] modes = {"🪜 Объясни по шагам", "💡 Дай подсказку", "✅ Проверь моё решение", "📖 Объясни тему простыми словами"};
        new AlertDialog.Builder(ctx)
                .setTitle("🧑‍🏫 ИИ-репетитор")
                .setView(box)
                .setPositiveButton("Дальше", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    new AlertDialog.Builder(ctx)
                            .setTitle("Как помочь?")
                            .setItems(modes, (d2, which) -> {
                                String[] how = {
                                        "Объясни решение по шагам, как хороший учитель школьнику: каждый шаг — почему так. В конце задай мне 1 похожую задачу для проверки.",
                                        "Не решай за меня. Дай одну наводящую подсказку, с чего начать, и спроси, что у меня получилось.",
                                        "Проверь моё решение: найди ошибки, объясни их и подскажи, как исправить. Не переписывай всё за меня.",
                                        "Объясни тему простыми словами, с примером из жизни, коротко. В конце — 2 вопроса для самопроверки.",
                                };
                                StarAi.streamDialog(this, "🧑‍🏫 Репетитор", how[which] + " Пиши по-русски, без markdown-таблиц.\n\n" + q, null, null);
                            })
                            .show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
