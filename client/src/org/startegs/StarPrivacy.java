/*
 * StarTegs 2.1 — фальшивый код-пароль, секретное слово в поиске, «прочитано» выборочно.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public final class StarPrivacy {

    private StarPrivacy() {}

    public static final int MENU_NOREAD = 9310;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_lock", Context.MODE_PRIVATE);
    }

    private static String sha(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("startegs-decoy:" + s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ================================================================ фальшивый код-пароль

    private static volatile boolean decoy;

    /** Открыто ли приложение фальшивым кодом: тогда скрыто всё личное, что добавляет мод. */
    public static boolean decoy() {
        return decoy;
    }

    public static boolean hasDecoy() {
        return prefs().contains("pin_decoy");
    }

    /**
     * Вызывается из PasscodeView.processDone вместо SharedConfig.checkPasscode.
     * Настоящий код — обычный режим, фальшивый — «чистый» режим.
     */
    public static boolean unlock(String password) {
        String d = prefs().getString("pin_decoy", null);
        if (d != null && d.equals(sha(password)) && !SharedConfig.checkPasscode(password)) {
            setDecoy(true);
            return true;
        }
        if (SharedConfig.checkPasscode(password)) {
            setDecoy(false);
            return true;
        }
        return false;
    }

    /** Вход по отпечатку — это владелец: обычный режим. */
    public static void realUnlock() {
        setDecoy(false);
    }

    private static void setDecoy(boolean on) {
        if (decoy == on) return;
        decoy = on;
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            }
        });
    }

    static void setupDecoy(BaseFragment fragment, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        if (SharedConfig.passcodeHash.length() == 0) {
            new AlertDialog.Builder(ctx)
                    .setTitle("🎭 Фальшивый код")
                    .setMessage("Сначала включите обычный код-пароль Telegram: Настройки → Конфиденциальность → Код-пароль. "
                            + "Фальшивый код вводится в том же окне при входе в приложение.")
                    .setPositiveButton("Понятно", null)
                    .show();
            return;
        }
        EditText input = new EditText(ctx);
        input.setInputType(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN
                ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN ? "4 цифры, не как настоящий" : "Пароль, не как настоящий");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle("🎭 Фальшивый код")
                .setMessage("Если при входе ввести этот код вместо настоящего, приложение откроется «чистым»: без скрытых и закрытых чатов, "
                        + "без кнопки и экрана StarTegs. Настоящий код возвращает всё как было.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (SharedConfig.passcodeType == SharedConfig.PASSCODE_TYPE_PIN && code.length() != 4) {
                        Toast.makeText(ctx, "Нужно 4 цифры, как у кода-пароля Telegram", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (code.length() < 4) {
                        Toast.makeText(ctx, "Слишком короткий код", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (SharedConfig.checkPasscode(code)) {
                        Toast.makeText(ctx, "Фальшивый код должен отличаться от настоящего", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs().edit().putString("pin_decoy", sha(code)).apply();
                    Toast.makeText(ctx, "🎭 Фальшивый код сохранён", Toast.LENGTH_SHORT).show();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null);
        if (hasDecoy()) b.setNeutralButton("Удалить", (d, w) -> {
            prefs().edit().remove("pin_decoy").apply();
            if (done != null) done.run();
        });
        b.show();
        AndroidUtilities.runOnUIThread(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 200);
    }

    // ================================================================ секретное слово в поиске

    public static boolean hasSecretWord() {
        return prefs().contains("pin_word");
    }

    /** Скрывать «Скрытые чаты» с экрана StarTegs — входить только по слову. */
    static boolean hideEntry() {
        return hasSecretWord() && prefs().getBoolean("word_only", false);
    }

    static void toggleHideEntry() {
        prefs().edit().putBoolean("word_only", !prefs().getBoolean("word_only", false)).apply();
    }

    /**
     * Вызывается из поиска DialogsActivity при каждом изменении текста.
     * Если введено секретное слово — очищаем поиск и открываем скрытые чаты.
     */
    public static boolean onSearch(BaseFragment fragment, EditText field, String text) {
        if (decoy || text == null || text.length() < 3 || !hasSecretWord()) return false;
        if (!sha(text.trim().toLowerCase()).equals(prefs().getString("pin_word", ""))) return false;
        field.setText("");
        AndroidUtilities.hideKeyboard(field);
        if (fragment.getParentActivity() == null) return true;
        AndroidUtilities.runOnUIThread(() -> {
            if (fragment.getActionBar() != null) fragment.getActionBar().closeSearchField();
            StarLock.askPinThen(fragment, () -> fragment.presentFragment(new StarHiddenActivity()));
        }, 150);
        return true;
    }

    static void setupWord(BaseFragment fragment, Runnable done) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setHint("Например: погода москва");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx)
                .setTitle("🔎 Секретное слово")
                .setMessage("Наберите это слово в обычном поиске над чатами — откроются скрытые чаты (после PIN или отпечатка). "
                        + "Выберите что-то обычное, что никто не наберёт случайно.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String word = input.getText().toString().trim().toLowerCase();
                    if (word.length() < 3) {
                        Toast.makeText(ctx, "Минимум 3 символа", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    prefs().edit().putString("pin_word", sha(word)).apply();
                    if (!StarLock.hasPin()) Toast.makeText(ctx, "Задайте ещё PIN-код — без него скрытые чаты откроются сразу", Toast.LENGTH_LONG).show();
                    if (done != null) done.run();
                })
                .setNegativeButton("Отмена", null);
        if (hasSecretWord()) b.setNeutralButton("Удалить", (d, w) -> {
            prefs().edit().remove("pin_word").remove("word_only").apply();
            if (done != null) done.run();
        });
        b.show();
    }

    // ================================================================ «прочитано» выборочно

    private static SharedPreferences modPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static Set<String> noReadSet() {
        return new HashSet<>(modPrefs().getStringSet("noread", new HashSet<>()));
    }

    /** Вызывается из MessagesController.completeReadTask: не отправлять «прочитано» этому чату. */
    public static boolean noRead(long dialogId) {
        return StarMod.isPro() && noReadSet().contains(String.valueOf(dialogId));
    }

    public static int noReadCount() {
        return noReadSet().size();
    }

    public static String noReadTitle(long dialogId) {
        return noReadSet().contains(String.valueOf(dialogId)) ? "Снова отправлять «прочитано»" : "Не отправлять «прочитано» этому чату";
    }

    public static void toggleNoRead(BaseFragment fragment, long dialogId) {
        if (!StarMod.requirePro(fragment, "Приватность")) return;
        Set<String> s = noReadSet();
        boolean on = !s.remove(String.valueOf(dialogId));
        if (on) s.add(String.valueOf(dialogId));
        modPrefs().edit().putStringSet("noread", s).apply();
        if (fragment.getParentActivity() != null) {
            Toast.makeText(fragment.getParentActivity(), on ? "🕶 Этот чат не увидит, что вы прочитали" : "«Прочитано» снова отправляется", Toast.LENGTH_SHORT).show();
        }
    }

    static void clearNoRead() {
        modPrefs().edit().remove("noread").apply();
    }
}
