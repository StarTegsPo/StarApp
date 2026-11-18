/*
 * StarTegs 4.6 — удобства, которые есть в популярных модах Telegram, в стиле StarTegs:
 * папки по типам чатов в один тап, прятать клавиатуру при прокрутке, без перехода к следующему каналу,
 * подтверждение звонков, «Подробности» сообщения, экономия батареи для анимаций мода.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.FilterCreateActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class StarPro {

    private StarPro() {}

    public static final int OPTION_DETAILS = 9070;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    static boolean flag(String k, boolean def) {
        return prefs().getBoolean(k, def);
    }

    static void toggle(String k, boolean def) {
        prefs().edit().putBoolean(k, !flag(k, def)).apply();
    }

    // ================================================================ переключатели

    public static boolean hideKeyboardOnScroll() { return flag("pro_kb_scroll", false); }

    public static boolean noNextChannel() { return flag("pro_no_next", false); }

    public static boolean confirmCalls() { return flag("pro_confirm_call", false); }

    public static boolean batterySaver() { return flag("pro_battery", true); }

    public static boolean detailsOn() { return flag("pro_details", true); }

    /**
     * Можно ли сейчас крутить анимации мода (рамки, частицы, дождь). Если включена «Экономия батареи»,
     * мод слушается режима энергосбережения Telegram: Настройки → Энергосбережение.
     */
    public static boolean anim(int liteFlag) {
        if (!batterySaver()) return true;
        try {
            return LiteMode.isEnabled(liteFlag);
        } catch (Throwable t) {
            return true;
        }
    }

    // ================================================================ клавиатура при прокрутке

    public static void onChatDrag(ChatActivity chat) {
        if (!hideKeyboardOnScroll() || chat == null) return;
        Activity a = chat.getParentActivity();
        if (a != null && a.getCurrentFocus() != null) AndroidUtilities.hideKeyboard(a.getCurrentFocus());
    }

    // ================================================================ подтверждение звонков

    private static boolean callConfirmed;

    /** true — показали вопрос, звонок начнётся только после «Позвонить». */
    public static boolean askBeforeCall(TLRPC.User user, boolean video, boolean canVideo, Activity activity, TLRPC.UserFull full, AccountInstance account) {
        if (callConfirmed) {
            callConfirmed = false;
            return false;
        }
        if (!confirmCalls() || activity == null || user == null) return false;
        String name = UserObject.getUserName(user);
        new AlertDialog.Builder(activity)
                .setTitle(video ? "📹 Видеозвонок" : "📞 Звонок")
                .setMessage("Позвонить " + name + "?")
                .setPositiveButton("Позвонить", (d, w) -> {
                    callConfirmed = true;
                    VoIPHelper.startCall(user, video, canVideo, activity, full, account);
                })
                .setNegativeButton("Отмена", null)
                .show();
        return true;
    }

    // ================================================================ «Подробности» сообщения

    public static boolean canDetails(MessageObject m) {
        return detailsOn() && m != null && m.messageOwner != null && m.getId() > 0;
    }

    private static String time(int unix) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(new Date(unix * 1000L));
    }

    private static String size(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f КБ", b / 1024f);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f МБ", b / 1048576f);
        return String.format(Locale.US, "%.2f ГБ", b / 1073741824f);
    }

    public static void details(ChatActivity chat, MessageObject m) {
        Activity a = chat.getParentActivity();
        if (a == null || m == null || m.messageOwner == null) return;
        TLRPC.Message o = m.messageOwner;
        MessagesController mc = chat.getMessagesController();
        StringBuilder sb = new StringBuilder();
        sb.append("🆔 ID сообщения: ").append(o.id).append('\n');
        sb.append("💬 ID чата: ").append(m.getDialogId()).append('\n');
        long from = m.getFromChatId();
        if (from != 0) {
            String who;
            if (from > 0) {
                TLRPC.User u = mc.getUser(from);
                who = u != null ? UserObject.getUserName(u) + (u.username != null ? " (@" + u.username + ")" : "") : "";
            } else {
                TLRPC.Chat c = mc.getChat(-from);
                who = c != null ? c.title : "";
            }
            sb.append("👤 Отправитель: ").append(who).append(" · ").append(from).append('\n');
        }
        sb.append("🕒 Отправлено: ").append(time(o.date)).append('\n');
        if (o.edit_date > 0) sb.append("✏️ Изменено: ").append(time(o.edit_date)).append('\n');
        if (o.fwd_from != null) {
            sb.append("↪️ Переслано");
            if (o.fwd_from.from_name != null) sb.append(" от ").append(o.fwd_from.from_name);
            if (o.fwd_from.date > 0) sb.append(", оригинал от ").append(time(o.fwd_from.date));
            sb.append('\n');
        }
        if (o.views > 0) sb.append("👁 Просмотры: ").append(o.views).append('\n');
        if (o.forwards > 0) sb.append("🔁 Пересылки: ").append(o.forwards).append('\n');
        if (o.grouped_id != 0) sb.append("🖼 Альбом: ").append(o.grouped_id).append('\n');
        TLRPC.Document doc = m.getDocument();
        if (doc != null) {
            String fn = FileLoader.getDocumentFileName(doc);
            if (!TextUtils.isEmpty(fn)) sb.append("📄 Файл: ").append(fn).append('\n');
            sb.append("📦 Размер: ").append(size(doc.size)).append('\n');
            if (!TextUtils.isEmpty(doc.mime_type)) sb.append("🧾 Тип: ").append(doc.mime_type).append('\n');
            sb.append("🌐 Дата-центр: DC").append(doc.dc_id).append('\n');
        } else if (o.media != null && o.media.photo != null) {
            TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(o.media.photo.sizes, AndroidUtilities.getPhotoSize());
            if (big != null) sb.append("🖼 Фото: ").append(big.w).append('×').append(big.h).append(", ").append(size(big.size)).append('\n');
            sb.append("🌐 Дата-центр: DC").append(o.media.photo.dc_id).append('\n');
        }
        String text = o.message;
        if (!TextUtils.isEmpty(text)) sb.append("🔤 Символов: ").append(text.length()).append('\n');
        String out = sb.toString().trim();
        new AlertDialog.Builder(a)
                .setTitle("🔍 Подробности")
                .setMessage(out)
                .setPositiveButton("Копировать", (d, w) -> {
                    AndroidUtilities.addToClipboard(out);
                    Toast.makeText(a, "Скопировано", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Копировать ID", (d, w) -> {
                    AndroidUtilities.addToClipboard(String.valueOf(o.id));
                    Toast.makeText(a, "ID скопирован", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    // ================================================================ папки по типам чатов

    static final String[] FOLDER_NAMES = {"👤 Личные", "👥 Группы", "📢 Каналы", "🤖 Боты", "🔴 Непрочитанные"};
    static final int[] FOLDER_FLAGS = {
            MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS,
            MessagesController.DIALOG_FILTER_FLAG_GROUPS,
            MessagesController.DIALOG_FILTER_FLAG_CHANNELS,
            MessagesController.DIALOG_FILTER_FLAG_BOTS,
            MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED,
    };

    public static void autoFolders(BaseFragment f, Runnable done) {
        Activity a = f.getParentActivity();
        if (a == null) return;
        String[] opts = new String[FOLDER_NAMES.length + 2];
        opts[0] = "✨ Набор: Личные, Группы, Каналы, Боты";
        opts[1] = "✨ Всё сразу (+ Непрочитанные)";
        for (int i = 0; i < FOLDER_NAMES.length; i++) opts[i + 2] = "Только «" + FOLDER_NAMES[i] + "»";
        new AlertDialog.Builder(a)
                .setTitle("📂 Папки по типам чатов")
                .setItems(opts, (d, w) -> {
                    boolean[] pick = new boolean[FOLDER_NAMES.length];
                    if (w == 0) pick[0] = pick[1] = pick[2] = pick[3] = true;
                    else if (w == 1) java.util.Arrays.fill(pick, true);
                    else pick[w - 2] = true;
                    createFolders(f, pick, done);
                })
                .show();
    }

    private static void createFolders(BaseFragment f, boolean[] pick, Runnable done) {
        MessagesController mc = f.getMessagesController();
        Set<String> have = new HashSet<>();
        Set<Integer> ids = new HashSet<>();
        for (MessagesController.DialogFilter df : mc.getDialogFilters()) {
            if (df.name != null) have.add(df.name.trim());
            ids.add(df.id);
        }
        ArrayList<Integer> todo = new ArrayList<>();
        for (int i = 0; i < pick.length; i++) if (pick[i] && !have.contains(FOLDER_NAMES[i])) todo.add(i);
        if (todo.isEmpty()) {
            Toast.makeText(f.getParentActivity(), "Такие папки уже есть", Toast.LENGTH_SHORT).show();
            return;
        }
        int limit = f.getUserConfig().isPremium() ? mc.dialogFiltersLimitPremium : mc.dialogFiltersLimitDefault;
        int now = Math.max(0, mc.getDialogFilters().size() - 1);
        if (now + todo.size() > limit) {
            Toast.makeText(f.getParentActivity(), "Telegram разрешает до " + limit + " папок — удалите лишние или выберите меньше", Toast.LENGTH_LONG).show();
            return;
        }
        next(f, todo, 0, ids, done);
    }

    private static void next(BaseFragment f, ArrayList<Integer> todo, int k, Set<Integer> ids, Runnable done) {
        if (k >= todo.size()) {
            if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "📂 Готово! Папки появились над списком чатов", Toast.LENGTH_LONG).show();
            if (done != null) done.run();
            return;
        }
        int i = todo.get(k);
        MessagesController.DialogFilter df = new MessagesController.DialogFilter();
        df.id = 2;
        while (ids.contains(df.id)) df.id++;
        ids.add(df.id);
        df.name = FOLDER_NAMES[i];
        df.color = -1;
        try {
            FilterCreateActivity.saveFilterToServer(df, FOLDER_FLAGS[i], FOLDER_NAMES[i], new ArrayList<>(), false, -1,
                    new ArrayList<>(), new ArrayList<>(), new org.telegram.messenger.support.LongSparseIntArray(),
                    true, false, true, true, k == 0, f, () -> next(f, todo, k + 1, ids, done));
        } catch (Throwable t) {
            FileLog.e(t);
            if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "Не получилось создать папку", Toast.LENGTH_SHORT).show();
        }
    }
}
