/*
 * StarTegs 3.0 — голосовые с эффектами: робот, бурундук, монстр, эхо.
 * Звук меняется прямо во время записи (16-бит PCM перед кодированием в Opus),
 * поэтому собеседник без мода тоже слышит эффект. Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.nio.ByteBuffer;

public final class StarVoice {

    private StarVoice() {}

    public static final int MENU_VOICE = 9015;
    static final String[] NAMES = {"🎙 Без эффекта", "🤖 Робот", "🐿 Бурундук", "👹 Монстр", "🏔 Эхо"};

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("startegs_mod", Context.MODE_PRIVATE);
    }

    public static int effect() {
        return prefs().getInt("voice_fx", 0);
    }

    /** Вызывается в MediaController.shouldUseAudioRecorderV2: с эффектом пишем старым рекордером, где есть доступ к звуку. */
    public static boolean active() {
        return effect() != 0;
    }

    public static void choose(BaseFragment f) {
        if (f.getParentActivity() == null) return;
        CharSequence[] items = new CharSequence[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) items[i] = (i == effect() ? "✓ " : "") + NAMES[i];
        new AlertDialog.Builder(f.getParentActivity())
                .setTitle("Голос для голосовых")
                .setItems(items, (d, which) -> {
                    prefs().edit().putInt("voice_fx", which).apply();
                    Toast.makeText(f.getParentActivity(), which == 0 ? "Обычный голос" : NAMES[which] + " — включено для всех голосовых, пока не выключите", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    static String title() {
        return NAMES[Math.max(0, Math.min(NAMES.length - 1, effect()))];
    }

    // ---------------------------------------------------------------- обработка звука

    private static final int RING = 1 << 15; // ~0,68 с при 48 кГц
    private static final float[] ring = new float[RING];
    private static int w;
    private static float phase, carrier;
    private static long lastCall;

    private static void resetIfNew() {
        long now = System.currentTimeMillis();
        if (now - lastCall > 1000) { // новая запись
            java.util.Arrays.fill(ring, 0f);
            w = 0;
            phase = 0;
            carrier = 0;
        }
        lastCall = now;
    }

    private static float read(float delay) {
        float pos = w - delay;
        while (pos < 0) pos += RING;
        int i0 = (int) pos;
        float fr = pos - i0;
        return ring[i0 % RING] * (1 - fr) + ring[(i0 + 1) % RING] * fr;
    }

    /** Сдвиг высоты без изменения длины: две «головки» чтения со скользящей задержкой и плавным переходом. */
    private static float pitch(float x, float factor, int window) {
        ring[w] = x;
        phase += (1f - factor) / window;
        if (phase >= 1f) phase -= 1f;
        if (phase < 0f) phase += 1f;
        float p2 = phase + 0.5f;
        if (p2 >= 1f) p2 -= 1f;
        float g1 = 1f - Math.abs(2f * phase - 1f), g2 = 1f - Math.abs(2f * p2 - 1f);
        float y = g1 * read(phase * window) + g2 * read(p2 * window);
        w = (w + 1) % RING;
        return y;
    }

    /**
     * Вызывается в цикле записи MediaController сразу после чтения с микрофона.
     * buffer — 16-битные отсчёты в нативном порядке байт, len — байт.
     */
    public static void process(ByteBuffer buffer, int len, int sampleRate) {
        int fx = effect();
        if (fx == 0 || buffer == null || len <= 0) return;
        try {
            resetIfNew();
            int n = len / 2;
            float sr = sampleRate > 0 ? sampleRate : 48000;
            for (int i = 0; i < n; i++) {
                float x = buffer.getShort(i * 2) / 32768f, y;
                switch (fx) {
                    case 1: { // робот: кольцевая модуляция + лёгкая «цифра»
                        carrier += 2 * Math.PI * 62 / sr;
                        if (carrier > 2 * Math.PI) carrier -= 2 * Math.PI;
                        y = x * (float) Math.sin(carrier) * 1.6f;
                        y = Math.round(y * 48) / 48f;
                        ring[w] = x;
                        w = (w + 1) % RING;
                        break;
                    }
                    case 2:
                        y = pitch(x, 1.6f, (int) (sr * 0.035f));
                        break;
                    case 3:
                        y = pitch(x, 0.68f, (int) (sr * 0.05f)) * 1.2f;
                        break;
                    default: { // эхо: 0,22 с с затуханием
                        float d = read(sr * 0.22f);
                        y = x + d * 0.5f;
                        ring[w] = x + d * 0.35f;
                        w = (w + 1) % RING;
                        break;
                    }
                }
                if (y > 1f) y = 1f;
                if (y < -1f) y = -1f;
                buffer.putShort(i * 2, (short) (y * 32767));
            }
        } catch (Exception ignore) {
        }
    }
}
