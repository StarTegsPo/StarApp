/*
 * StarTegs — замок на отдельные чаты: открываются только по PIN-коду.
 * Лицензия: GNU GPL v2 или новее.
 *
 * PIN хранится только в виде SHA-256 с солью. После правильного ввода чат
 * остаётся открытым 5 минут, чтобы не спрашивать PIN при каждом возврате в него.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class StarLock {

    private StarLock() {}

    private static final long UNLOCK_MS = 5 * 60_000L;
    private static final HashMap<Long, Long> unlockedUntil = new HashMap<>();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_lock", Context.MODE_PRIVATE);
    }

    private static Set<String> lockedSet() {
        return new HashSet<>(prefs().getStringSet("locked", new HashSet<>()));
    }

    public static boolean isLocked(long dialogId) {
        return dialogId != 0 && lockedSet().contains(String.valueOf(dialogId));
    }

    public static int lockedCount() {
        return lockedSet().size();
    }

    public static boolean hasPin() {
        return prefs().contains("pin_hash");
    }

    // ---------------------------------------------------------------- PIN

    private static String hash(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((salt + ":" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean checkPin(String pin) {
        String salt = prefs().getString("pin_salt", "");
        return hasPin() && hash(pin, salt).equals(prefs().getString("pin_hash", ""));
    }

    private static void savePin(String pin) {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        StringBuilder salt = new StringBuilder();
        for (byte b : s) salt.append(String.format("%02x", b));
        prefs().edit().putString("pin_salt", salt.toString()).putString("pin_hash", hash(pin, salt.toString())).apply();
    }

    private interface PinCallback {
        void run(String pin);
    }

    private static void askPin(Context ctx, String title, String message, PinCallback onPin, Runnable onCancel) {
        if (ctx == null) return;
        EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(22);
        input.setHint("PIN");
        FrameLayout box = new FrameLayout(ctx);
        int pad = AndroidUtilities.dp(24);
        box.setPadding(pad, 0, pad, 0);
        box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> onPin.run(input.getText().toString().trim()))
                .setNegativeButton("Отмена", (d, w) -> {
                    if (onCancel != null) onCancel.run();
                })
                .create();
        dialog.show();
        AndroidUtilities.runOnUIThread(() -> {
            input.requestFocus();
            AndroidUtilities.showKeyboard(input);
        }, 200);
    }

    /** Создать или сменить PIN (вводится дважды). */
    public static void setupPin(BaseFragment fragment, Runnable done) {
        Context ctx = fragment.getParentActivity();
        askPin(ctx, "🔒 Новый PIN", "Придумайте PIN из 4–8 цифр для закрытых чатов", pin1 -> {
            if (pin1.length() < 4 || pin1.length() > 8) {
                Toast.makeText(ctx, "PIN должен быть из 4–8 цифр", Toast.LENGTH_SHORT).show();
                return;
            }
            askPin(ctx, "🔒 Повторите PIN", null, pin2 -> {
                if (!pin1.equals(pin2)) {
                    Toast.makeText(ctx, "PIN не совпал, попробуйте ещё раз", Toast.LENGTH_SHORT).show();
                    return;
                }
                savePin(pin1);
                Toast.makeText(ctx, "PIN сохранён", Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            }, null);
        }, null);
    }

    // ---------------------------------------------------------------- отпечаток пальца

    public static boolean biometricEnabled() {
        return prefs().getBoolean("biometric", true);
    }

    static void setBiometric(boolean on) {
        prefs().edit().putBoolean("biometric", on).apply();
    }

    public static boolean biometricAvailable() {
        try {
            return androidx.biometric.BiometricManager.from(ApplicationLoader.applicationContext)
                    .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Проверить владельца: сначала отпечаток (если включён и есть на телефоне), при отказе
     * или ошибке — PIN. ok выполняется только после успешной проверки.
     */
    static void verify(Context ctx, String title, Runnable ok) {
        Runnable byPin = () -> askPin(ctx, title, "Введите PIN", pin -> {
            if (checkPin(pin)) ok.run();
            else Toast.makeText(ctx, "Неверный PIN", Toast.LENGTH_SHORT).show();
        }, null);
        if (!biometricEnabled() || !biometricAvailable() || LaunchActivity.instance == null) {
            byPin.run();
            return;
        }
        try {
            androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(LaunchActivity.instance,
                    androidx.core.content.ContextCompat.getMainExecutor(ctx), new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    ok.run();
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    byPin.run();
                }
            });
            prompt.authenticate(new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("Приложите палец")
                    .setNegativeButtonText("Ввести PIN")
                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build());
        } catch (Throwable e) {
            FileLog.e(e);
            byPin.run();
        }
    }

    // ---------------------------------------------------------------- замок

    /** Пункт профиля: закрыть чат замком или снять замок. */
    public static void toggleLock(BaseFragment fragment, long dialogId) {
        if (dialogId == 0) return;
        if (isLocked(dialogId)) {
            verify(fragment.getParentActivity(), "🔓 Снять замок", () -> {
                Set<String> s = lockedSet();
                s.remove(String.valueOf(dialogId));
                prefs().edit().putStringSet("locked", s).apply();
                Toast.makeText(fragment.getParentActivity(), "Замок снят", Toast.LENGTH_SHORT).show();
            });
            return;
        }
        if (!StarMod.requirePro(fragment, "Замок на чаты")) return;
        Runnable lock = () -> {
            Set<String> s = lockedSet();
            s.add(String.valueOf(dialogId));
            prefs().edit().putStringSet("locked", s).apply();
            unlockedUntil.put(dialogId, System.currentTimeMillis() + UNLOCK_MS);
            Toast.makeText(fragment.getParentActivity(), "🔒 Чат закрыт PIN-кодом", Toast.LENGTH_SHORT).show();
        };
        if (hasPin()) lock.run(); else setupPin(fragment, lock);
    }

    /** Спросить PIN и, если он верный, выполнить действие (например, открыть скрытые чаты). */
    public static void askPinThen(BaseFragment fragment, Runnable ok) {
        if (!hasPin()) {
            setupPin(fragment, ok);
            return;
        }
        verify(fragment.getParentActivity(), "🔒 Подтвердите, что это вы", ok);
    }

    public static void unlockAll(BaseFragment fragment, Runnable done) {
        verify(fragment.getParentActivity(), "🔓 Снять все замки", () -> {
            prefs().edit().remove("locked").apply();
            if (done != null) done.run();
        });
    }

    /**
     * Вызывается в начале ChatActivity.onFragmentCreate. Если чат закрыт, показывает
     * запрос PIN и возвращает false (чат не открывается); после верного PIN открывает
     * его заново с теми же параметрами.
     */
    public static boolean allowOpen(ChatActivity chat, long dialogId) {
        try {
            if (!isLocked(dialogId)) return true;
            Long until = unlockedUntil.get(dialogId);
            if (until != null && until > System.currentTimeMillis()) return true;
            final Bundle args = chat.getArguments();
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment last = LaunchActivity.getLastFragment();
                if (last == null || last.getParentActivity() == null) return;
                verify(last.getParentActivity(), "🔒 Закрытый чат", () -> {
                    unlockedUntil.put(dialogId, System.currentTimeMillis() + UNLOCK_MS);
                    last.presentFragment(new ChatActivity(args));
                });
            });
            return false;
        } catch (Exception e) {
            FileLog.e(e);
            return true; // при сбое замка не блокируем доступ к чату
        }
    }
}
