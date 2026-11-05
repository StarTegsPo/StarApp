/*
 * StarTegs — заметки к людям и экспорт чата. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StarTools {

    private StarTools() {}

    public static final int MENU_NOTE = 9305;
    public static final int MENU_STATS = 9306;
    public static final int MENU_EXPORT = 9307;

    private static SharedPreferences notes() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_notes", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- заметки

    public static String note(long dialogId) {
        return notes().getString(String.valueOf(dialogId), "");
    }

    public static Map<String, ?> allNotes() {
        return notes().getAll();
    }

    /** Подпись пункта меню профиля. */
    public static String noteMenuTitle(long dialogId) {
        String n = note(dialogId);
        if (n.isEmpty()) return "Добавить заметку";
        return "Заметка: " + (n.length() > 22 ? n.substring(0, 22) + "…" : n).replace('\n', ' ');
    }

    public static void editNote(BaseFragment fragment, long dialogId, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || dialogId == 0) return;
        EditText input = new EditText(ctx);
        input.setText(note(dialogId));
        input.setMinLines(2);
        input.setHint("Видна только вам");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("📝 Заметка")
                .setMessage("Например: «должен 500 ₽», «клиент из Маркета». Хранится только на этом телефоне.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String t = input.getText().toString().trim();
                    if (t.isEmpty()) notes().edit().remove(String.valueOf(dialogId)).apply();
                    else notes().edit().putString(String.valueOf(dialogId), t).apply();
                    Toast.makeText(ctx, t.isEmpty() ? "Заметка удалена" : "Заметка сохранена", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    static String dialogTitle(int account, long dialogId) {
        MessagesController mc = MessagesController.getInstance(account);
        if (dialogId > 0) {
            TLRPC.User u = mc.getUser(dialogId);
            return u != null ? UserObject.getUserName(u) : "Чат " + dialogId;
        }
        TLRPC.Chat c = mc.getChat(-dialogId);
        return c != null ? c.title : "Чат " + dialogId;
    }

    // ---------------------------------------------------------------- чтение сообщений чата из базы Telegram

    static final class Msg {
        int mid, date;
        long from;
        boolean out;
        String text;
        boolean media;
    }

    /** Сообщения чата, загруженные в приложение (новые первыми). Вызывать на очереди хранилища. */
    static ArrayList<Msg> readMessages(int account, long dialogId, int limit) {
        ArrayList<Msg> out = new ArrayList<>();
        MessagesStorage storage = MessagesStorage.getInstance(account);
        SQLiteCursor c = null;
        try {
            c = storage.getDatabase().queryFinalized("SELECT data, out, date, mid FROM messages_v2 WHERE uid = " + dialogId + " ORDER BY mid DESC LIMIT " + limit);
            while (c.next()) {
                NativeByteBuffer data = c.byteBufferValue(0);
                if (data == null) continue;
                TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                data.reuse();
                if (m == null || m instanceof TLRPC.TL_messageService) continue;
                Msg x = new Msg();
                x.out = c.intValue(1) == 1;
                x.date = c.intValue(2);
                x.mid = c.intValue(3);
                x.from = m.from_id != null ? (m.from_id.user_id != 0 ? m.from_id.user_id : -m.from_id.channel_id) : dialogId;
                x.text = m.message != null ? m.message : "";
                x.media = m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty);
                out.add(x);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (c != null) c.dispose();
        }
        return out;
    }

    // ---------------------------------------------------------------- экспорт

    public static void exportChat(BaseFragment fragment, long dialogId) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || dialogId == 0) return;
        new AlertDialog.Builder(ctx)
                .setTitle("💾 Экспорт чата")
                .setItems(new CharSequence[]{"📄 Текст (.txt)", "🌐 Страница (.html)"}, (d, which) -> doExport(fragment, dialogId, which == 1))
                .show();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static void doExport(BaseFragment fragment, long dialogId, boolean html) {
        final int account = fragment.getCurrentAccount();
        final Context ctx = fragment.getParentActivity();
        final String title = dialogTitle(account, dialogId);
        Toast.makeText(ctx, "Готовлю файл…", Toast.LENGTH_SHORT).show();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            ArrayList<Msg> list = readMessages(account, dialogId, 50000);
            AndroidUtilities.runOnUIThread(() -> {
                if (list.isEmpty()) {
                    Toast.makeText(ctx, "В приложении нет сообщений этого чата", Toast.LENGTH_SHORT).show();
                    return;
                }
                MessagesController mc = MessagesController.getInstance(account);
                HashMap<Long, String> names = new HashMap<>();
                long self = fragment.getUserConfig().getClientUserId();
                SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
                long scope = dialogId < 0 && org.telegram.messenger.ChatObject.isChannel(mc.getChat(-dialogId)) ? dialogId : 0;
                try {
                    File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
                    dir.mkdirs();
                    String safe = title.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
                    File file = new File(dir, "StarTegs_" + (safe.isEmpty() ? "chat" : safe) + (html ? ".html" : ".txt"));
                    int deleted = 0;
                    try (Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                        if (html) {
                            w.write("<!doctype html><meta charset=utf-8><title>" + esc(title) + "</title><style>body{font:15px sans-serif;max-width:760px;margin:20px auto;padding:0 12px;background:#f3f2fa}"
                                    + ".m{background:#fff;border-radius:12px;padding:8px 12px;margin:6px 0}.o{background:#e6e0ff;margin-left:15%}.d{opacity:.7;border:1px dashed #e44}"
                                    + "small{color:#888}</style><h2>" + esc(title) + "</h2><p><small>Экспорт StarTegs · " + list.size() + " сообщений</small></p>");
                        } else {
                            w.write(title + "\nЭкспорт StarTegs · " + list.size() + " сообщений\n\n");
                        }
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Msg m = list.get(i);
                            String who;
                            if (m.out || m.from == self) {
                                who = "Я";
                            } else {
                                who = names.get(m.from);
                                if (who == null) {
                                    if (m.from > 0) {
                                        TLRPC.User u = mc.getUser(m.from);
                                        who = u != null ? UserObject.getUserName(u) : "Пользователь " + m.from;
                                    } else {
                                        TLRPC.Chat ch = mc.getChat(-m.from);
                                        who = ch != null ? ch.title : "Канал";
                                    }
                                    names.put(m.from, who);
                                }
                            }
                            boolean del = StarMod.isDeletedKey(account, scope, m.mid);
                            if (del) deleted++;
                            String text = m.text.isEmpty() ? (m.media ? "[медиа]" : "[сообщение]") : (m.media ? "[медиа] " + m.text : m.text);
                            String when = f.format(new Date((long) m.date * 1000));
                            if (html) {
                                w.write("<div class='m" + (m.out ? " o" : "") + (del ? " d" : "") + "'><small>" + esc(who) + " · " + when + (del ? " · 🗑 удалено" : "") + "</small><br>" + esc(text) + "</div>");
                            } else {
                                w.write("[" + when + "] " + who + (del ? " (🗑 удалено)" : "") + ": " + text + "\n");
                            }
                        }
                    }
                    Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(html ? "text/html" : "text/plain");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(Intent.createChooser(send, "Экспорт: " + list.size() + " сообщ." + (deleted > 0 ? ", удалённых " + deleted : "")));
                } catch (Exception e) {
                    FileLog.e(e);
                    Toast.makeText(ctx, "Не удалось сохранить файл", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
