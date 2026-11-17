/*
 * StarTegs 4.2 — отправка и медиа:
 *   • подтверждение перед отправкой стикеров, GIF и голосовых (от случайных нажатий);
 *   • тихая отправка по умолчанию (без звука у получателя);
 *   • «Скачать все медиа» из чата — фото, видео, файлы и музыка из загруженной части переписки;
 *   • скрыть кнопку «Поделиться» рядом с сообщениями;
 *   • кружки — сразу на основную (заднюю) камеру.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public final class StarMedia {

    private StarMedia() {}

    public static final int MENU_SAVE_ALL = 9334;

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    private static boolean flag(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    static void toggle(String key, boolean def) {
        prefs().edit().putBoolean(key, !flag(key, def)).apply();
    }

    public static boolean confirmStickers() { return flag("media_confirm_stickers", false); }
    public static boolean confirmVoice() { return flag("media_confirm_voice", false); }
    public static boolean silentDefault() { return flag("media_silent", false); }
    public static boolean hideShareButton() { return flag("media_hide_share", false); }
    public static boolean roundBackCamera() { return flag("media_round_back", false); }

    /** Для InstantCameraView: с какой камеры начинать кружок. */
    public static boolean roundFront() {
        try {
            return !roundBackCamera();
        } catch (Exception e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- отправка

    private static boolean isSticker(SendMessagesHelper.SendMessageParams p) {
        return p.document != null && (MessageObject.isStickerDocument(p.document) || MessageObject.isAnimatedStickerDocument(p.document, true)
                || MessageObject.isGifDocument(p.document));
    }

    static boolean isVoice(SendMessagesHelper.SendMessageParams p) {
        return p.document != null && MessageObject.isVoiceDocument(p.document);
    }

    /**
     * Из StarSend.intercept. true — сообщение забрали: спросили подтверждение,
     * по «Отправить» вызовется then.run().
     */
    static boolean confirm(SendMessagesHelper.SendMessageParams p, Runnable then) {
        try {
            boolean sticker = confirmStickers() && isSticker(p);
            boolean voice = confirmVoice() && isVoice(p);
            if (!sticker && !voice) return false;
            BaseFragment f = LaunchActivity.getLastFragment();
            if (f == null || f.getParentActivity() == null) return false;
            String what = voice ? "голосовое" : MessageObject.isGifDocument(p.document) ? "GIF" : "стикер";
            AlertDialog d = new AlertDialog.Builder(f.getParentActivity())
                    .setTitle("Отправить " + what + "?")
                    .setMessage("Подтверждение включено в StarTegs → Разделы → Отправка.")
                    .setPositiveButton("Отправить", (dialog, w) -> then.run())
                    .setNegativeButton("Отмена", null)
                    .create();
            d.show();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    // ---------------------------------------------------------------- скачать всё

    public static void saveAll(ChatActivity chat) {
        if (chat == null || chat.getParentActivity() == null) return;
        if (!StarMod.requirePro(chat, "Скачать все медиа из чата")) return;
        ArrayList<MessageObject> list = chat.messages;
        ArrayList<MessageObject> media = new ArrayList<>();
        if (list != null) {
            for (MessageObject m : list) {
                if (m == null || m.messageOwner == null || m.getId() <= 0 || m.messageOwner.noforwards) continue;
                if (m.isPhoto() || m.isVideo() || m.isMusic() || m.isGif()
                        || (m.getDocument() != null && !m.isSticker() && !m.isAnimatedSticker() && !m.isVoice() && !m.isRoundVideo())) {
                    media.add(m);
                }
            }
        }
        if (media.isEmpty()) {
            Toast.makeText(chat.getParentActivity(), "В загруженной части чата нет медиа. Пролистайте выше и попробуйте снова.", Toast.LENGTH_LONG).show();
            return;
        }
        if (chat.getCurrentChat() != null && chat.getCurrentChat().noforwards) {
            Toast.makeText(chat.getParentActivity(), "Владелец чата запретил сохранять содержимое", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(chat.getParentActivity())
                .setTitle("📥 Скачать все медиа")
                .setMessage("Найдено файлов: " + media.size() + " (в загруженной части переписки). Фото и видео попадут в галерею, файлы и музыка — в «Загрузки».")
                .setPositiveButton("Скачать", (d, w) -> {
                    try {
                        MediaController.saveFilesFromMessages(chat.getParentActivity(), AccountInstance.getInstance(chat.getCurrentAccount()), media,
                                count -> Toast.makeText(ApplicationLoader.applicationContext, "📥 Сохранено: " + count, Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton("Отмена", null).show();
    }
}
