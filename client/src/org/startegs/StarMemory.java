/*
 * StarTegs 4.9 — «Память»: одно нажатие — и сообщение навсегда копируется в твою память
 * (текст, кто написал, из какого чата, когда, плюс копия уже загруженного фото, видео или файла).
 * Копия остаётся, даже если сообщение потом удалят. Категории, поиск, вопрос ИИ по памяти.
 * Хранится только на этом телефоне. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class StarMemory {

    private StarMemory() {}

    public static final int OPTION_MEMORY = 9080;
    private static final int MAX = 2000;

    static final String[] CATS = {"🗂 Без категории", "🔥 Важное", "📚 Учёба", "😂 Смешное", "💡 Идеи", "💬 Цитаты", "❤️ Тёплое"};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_memory", Context.MODE_PRIVATE);
    }

    static JSONArray all() {
        try {
            return new JSONArray(prefs().getString("items", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(JSONArray a) {
        prefs().edit().putString("items", a.toString()).apply();
    }

    public static int count() {
        return all().length();
    }

    private static int indexOf(JSONArray a, int account, long dialog, int mid) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("a") == account && o.optLong("d") == dialog && o.optInt("m") == mid) return i;
        }
        return -1;
    }

    public static boolean can(MessageObject m) {
        return m != null && m.messageOwner != null && m.getId() > 0 && !m.isSponsored();
    }

    public static String title(int account, MessageObject m) {
        return m != null && indexOf(all(), account, m.getDialogId(), m.getId()) >= 0 ? "🧠 Убрать из памяти" : "🧠 В память";
    }

    static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "startegs_memory");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Одно нажатие: сохранить (или убрать, если уже в памяти). */
    public static void toggle(BaseFragment f, MessageObject m) {
        if (!can(m)) return;
        int account = f.getCurrentAccount();
        Context ctx = f.getParentActivity();
        JSONArray a = all();
        int i = indexOf(a, account, m.getDialogId(), m.getId());
        if (i >= 0) {
            remove(a, i);
            if (ctx != null) Toast.makeText(ctx, "🧠 Убрано из памяти", Toast.LENGTH_SHORT).show();
            return;
        }
        if (a.length() >= MAX) {
            if (ctx != null) Toast.makeText(ctx, "🧠 В памяти уже " + MAX + " сообщений — убери старые", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            TLRPC.Message msg = m.messageOwner;
            MessagesController mc = MessagesController.getInstance(account);
            String text = msg.message == null ? "" : msg.message;
            if (text.length() > 4000) text = text.substring(0, 4000);
            JSONObject o = new JSONObject()
                    .put("a", account).put("d", m.getDialogId()).put("m", m.getId())
                    .put("t", text).put("md", StarVault.preview(msg).replace(text, "").trim())
                    .put("who", StarVault.sender(mc, msg, m.getDialogId()))
                    .put("w", StarVault.title(mc, m.getDialogId()))
                    .put("dt", msg.date).put("at", System.currentTimeMillis() / 1000).put("c", 0);
            String file = copyMedia(account, msg);
            if (file != null) o.put("f", file);
            a.put(o);
            save(a);
            StarAchieve.bump("memory", 1);
            if (ctx != null) Toast.makeText(ctx, "🧠 Запомнил! В памяти: " + a.length(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void remove(JSONArray a, int i) {
        JSONObject o = a.optJSONObject(i);
        if (o != null && o.has("f")) new File(o.optString("f")).delete();
        JSONArray out = new JSONArray();
        for (int k = 0; k < a.length(); k++) if (k != i) out.put(a.opt(k));
        save(out);
    }

    /** Копия уже загруженного файла (одноразовые — никогда). */
    private static String copyMedia(int account, TLRPC.Message m) {
        try {
            if (m.media == null || m.media.ttl_seconds != 0) return null;
            File src = FileLoader.getInstance(account).getPathToMessage(m);
            if (src == null || !src.exists() || src.length() == 0 || src.length() > 100L * 1024 * 1024) return null;
            String name = src.getName();
            String ext = name.lastIndexOf('.') > 0 ? name.substring(name.lastIndexOf('.') + 1) : "bin";
            File dst = new File(dir(), account + "_" + m.dialog_id + "_" + m.id + "_" + System.currentTimeMillis() + "." + ext);
            new Thread(() -> {
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
            return dst.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================ экран «Память»

    public static class Screen extends UniversalFragment {

        private static final int ID_ITEM = 10000, ID_ASK = 2, ID_CAT = 100;
        private final ArrayList<Integer> shown = new ArrayList<>();
        private String query = "";
        private int cat = -1;

        @Override
        protected CharSequence getTitle() {
            return "🧠 Память";
        }

        @Override
        public View createView(Context context) {
            View v = super.createView(context);
            ActionBarMenuItem s = actionBar.createMenu().addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                    .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                        @Override
                        public void onTextChanged(EditText e) {
                            query = e.getText().toString().trim().toLowerCase(Locale.ROOT);
                            refresh();
                        }

                        @Override
                        public void onSearchCollapse() {
                            query = "";
                            refresh();
                        }
                    });
            s.setSearchFieldHint("Поиск в памяти");
            return v;
        }

        private void refresh() {
            if (listView != null) listView.adapter.update(true);
        }

        @Override
        public void onResume() {
            super.onResume();
            refresh();
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            shown.clear();
            JSONArray a = all();
            if (StarAi.enabled() && a.length() > 0) items.add(UItem.asButton(ID_ASK, R.drawable.msg_bot, "🤖 Спросить ИИ по памяти").accent());
            items.add(UItem.asHeader("Категории"));
            items.add(UItem.asRadio(ID_CAT - 1 + 0, "Все · " + a.length()).setChecked(cat == -1));
            for (int c = 0; c < CATS.length; c++) {
                int n = 0;
                for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i).optInt("c") == c) n++;
                if (n > 0 || cat == c) items.add(UItem.asRadio(ID_CAT + c, CATS[c] + " · " + n).setChecked(cat == c));
            }
            items.add(UItem.asHeader("Сообщения"));
            SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy", new Locale("ru"));
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (cat >= 0 && o.optInt("c") != cat) continue;
                String full = (o.optString("md") + " " + o.optString("t")).trim();
                if (!query.isEmpty() && !(full.toLowerCase(Locale.ROOT).contains(query) || o.optString("who").toLowerCase(Locale.ROOT).contains(query)
                        || o.optString("w").toLowerCase(Locale.ROOT).contains(query))) continue;
                String t = full.replace('\n', ' ');
                if (t.isEmpty()) t = "[медиа]";
                if (t.length() > 110) t = t.substring(0, 110) + "…";
                long dt = o.optLong("dt");
                String sub = o.optString("who") + " · " + o.optString("w") + (dt > 0 ? " · " + f.format(new Date(dt * 1000)) : "")
                        + (o.has("f") ? " · 📎" : "") + (o.optInt("c") > 0 ? " · " + CATS[o.optInt("c")].split(" ")[0] : "");
                items.add(UItem.asButton(ID_ITEM + i, t, sub));
                shown.add(i);
            }
            if (a.length() == 0 && getParentActivity() != null) items.add(UItem.asCustom(StarGlow.empty(getParentActivity(), "🧠", "Память пока пуста",
                    "Долгое нажатие на любое сообщение → «🧠 В память». Или выбери «В память» для двойного тапа — хватит двух касаний.")));
            else if (shown.isEmpty()) items.add(UItem.asShadow("Ничего не найдено"));
            else items.add(UItem.asShadow("Нажми — действия. Копия остаётся, даже если сообщение удалят. Хранится только на этом телефоне."));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            int id = item.id;
            if (id == ID_ASK) {
                askAi();
                return;
            }
            if (id >= ID_CAT - 1 && id < ID_CAT + CATS.length) {
                cat = id - ID_CAT;
                refresh();
                return;
            }
            int i = id - ID_ITEM;
            JSONArray a = all();
            JSONObject o = a.optJSONObject(i);
            Activity act = getParentActivity();
            if (o == null || act == null) return;
            boolean hasFile = o.has("f") && new File(o.optString("f")).exists();
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<Runnable> acts = new ArrayList<>();
            labels.add("💬 Открыть в чате");
            acts.add(() -> {
                Bundle args = new Bundle();
                long d = o.optLong("d");
                if (d > 0) args.putLong("user_id", d);
                else args.putLong("chat_id", -d);
                args.putInt("message_id", o.optInt("m"));
                presentFragment(new ChatActivity(args));
            });
            labels.add("📋 Копировать текст");
            acts.add(() -> {
                AndroidUtilities.addToClipboard(o.optString("t"));
                Toast.makeText(act, "Скопировано", Toast.LENGTH_SHORT).show();
            });
            labels.add("⭐ Отправить себе в «Избранное»");
            acts.add(() -> {
                int account = UserConfig.selectedAccount;
                long self = UserConfig.getInstance(account).getClientUserId();
                String text = "🧠 Из памяти · " + o.optString("who") + " · " + o.optString("w") + "\n\n" + (o.optString("md") + " " + o.optString("t")).trim();
                StarSend.direct(account, SendMessagesHelper.SendMessageParams.of(text, self, null, null, null, false, null, null, null, true, 0, 0, null, false));
                Toast.makeText(act, "⭐ Отправлено в «Избранное»", Toast.LENGTH_SHORT).show();
            });
            if (hasFile) {
                labels.add("📥 Сохранить или отправить файл");
                acts.add(() -> StarVault.shareFile(act, new File(o.optString("f"))));
            }
            labels.add("🏷 Категория");
            acts.add(() -> new AlertDialog.Builder(act).setTitle("🏷 Категория").setItems(CATS, (d, w) -> {
                try {
                    JSONArray b = all();
                    b.optJSONObject(i).put("c", w);
                    save(b);
                } catch (Exception ignore) {
                }
                refresh();
            }).show());
            labels.add("🗑 Убрать из памяти");
            acts.add(() -> {
                remove(all(), i);
                refresh();
            });
            new AlertDialog.Builder(act).setTitle(o.optString("who") + " · " + o.optString("w"))
                    .setItems(labels.toArray(new CharSequence[0]), (d, w) -> acts.get(w).run()).show();
        }

        private void askAi() {
            Activity act = getParentActivity();
            if (act == null) return;
            EditText in = new EditText(act);
            in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            in.setHint("Например: когда у Маши день рождения?");
            FrameLayout box = new FrameLayout(act);
            int pad = AndroidUtilities.dp(24);
            box.setPadding(pad, 0, pad, 0);
            box.addView(in);
            new AlertDialog.Builder(act).setTitle("🤖 Вопрос по памяти").setView(box).setPositiveButton("Спросить", (d, w) -> {
                String q = in.getText().toString().trim();
                if (q.isEmpty()) return;
                JSONArray a = all();
                StringBuilder ctx = new StringBuilder();
                for (int i = a.length() - 1; i >= 0 && ctx.length() < 9000; i--) {
                    JSONObject o = a.optJSONObject(i);
                    ctx.append("- ").append(o.optString("who")).append(" (").append(o.optString("w")).append("): ")
                            .append((o.optString("md") + " " + o.optString("t")).trim()).append('\n');
                }
                String prompt = "Вот сообщения, которые пользователь сохранил себе в память:\n" + ctx
                        + "\nОтветь на вопрос только по этим сообщениям, коротко и по-русски. Если ответа нет — так и скажи.\nВопрос: " + q;
                Toast.makeText(act, "🤖 Ищу в памяти…", Toast.LENGTH_SHORT).show();
                StarAi.ask(currentAccount, prompt, 0, new StarAi.Stream() {
                    @Override
                    public void partial(String text) {
                    }

                    @Override
                    public void done(String text) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null) return;
                            new AlertDialog.Builder(getParentActivity()).setTitle("🧠 " + q)
                                    .setMessage(TextUtils.isEmpty(text) ? "ИИ не ответил" : text.trim())
                                    .setPositiveButton("OK", null).show();
                        });
                    }

                    @Override
                    public void error(String why) {
                        AndroidUtilities.runOnUIThread(() -> Toast.makeText(act, "ИИ: " + why, Toast.LENGTH_SHORT).show());
                    }
                });
            }).setNegativeButton("Отмена", null).show();
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
