/*
 * StarTegs 3.1 — голосовые-караоке: выбираете трек из чата (долгое нажатие на музыку → «Караоке»),
 * записываете голосовое — минусовка подмешивается в запись и звучит в наушниках.
 * Лицензия: GNU GPL v2 или новее.
 */
package org.startegs;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class StarKaraoke {

    private StarKaraoke() {}

    public static final int OPTION_KARAOKE = 9007;
    private static final int RATE = 48000;
    private static final int MAX_SECONDS = 240;

    private static volatile short[] track;
    private static volatile int pos;
    private static volatile String title;
    private static AudioTrack player;
    private static long lastProcess;

    public static boolean armed() {
        return track != null;
    }

    static String title() {
        return title;
    }

    public static boolean canUse(MessageObject m) {
        return m != null && (m.isMusic() || m.isVoice()) && m.getDocument() != null;
    }

    public static String optionTitle() {
        return armed() ? "🎤 Караоке: выключить" : "🎤 Караоке на этот трек";
    }

    /** Долгое нажатие на музыку → «Караоке». */
    public static void toggle(BaseFragment f, MessageObject m) {
        if (armed()) {
            disarm();
            if (f.getParentActivity() != null) Toast.makeText(f.getParentActivity(), "Караоке выключено", Toast.LENGTH_SHORT).show();
            return;
        }
        File file = FileLoader.getInstance(f.getCurrentAccount()).getPathToMessage(m.messageOwner);
        if (file == null || !file.exists()) {
            Toast.makeText(f.getParentActivity(), "Сначала включите трек, чтобы он загрузился", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(f.getParentActivity(), "🎤 Готовлю минусовку…", Toast.LENGTH_SHORT).show();
        String name = m.getMusicTitle() != null ? m.getMusicTitle() : "трек";
        Utilities.globalQueue.postRunnable(() -> {
            short[] pcm = decode(file);
            AndroidUtilities.runOnUIThread(() -> {
                if (pcm == null || pcm.length < RATE) {
                    Toast.makeText(f.getParentActivity(), "Не удалось прочитать этот трек", Toast.LENGTH_LONG).show();
                    return;
                }
                track = pcm;
                pos = 0;
                title = name;
                Toast.makeText(f.getParentActivity(), "🎤 Караоке «" + name + "» включено! Наденьте наушники и запишите голосовое — музыка начнётся с записью", Toast.LENGTH_LONG).show();
            });
        });
    }

    static void disarm() {
        stopPlayer();
        track = null;
        title = null;
    }

    /** Декодирует аудио в моно 48 кГц, до 4 минут. */
    private static short[] decode(File f) {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(f.getAbsolutePath());
            int ti = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat mf = ex.getTrackFormat(i);
                String mime = mf.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    ti = i;
                    fmt = mf;
                    break;
                }
            }
            if (ti < 0) return null;
            ex.selectTrack(ti);
            int srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            int maxOut = MAX_SECONDS * RATE;
            short[] out = new short[maxOut];
            int n = 0;
            double step = (double) srcRate / RATE, acc = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;
            while (!outDone && n < maxOut) {
                if (!inDone) {
                    int ib = codec.dequeueInputBuffer(10000);
                    if (ib >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(ib);
                        int size = ex.readSampleData(buf, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(ib, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ib, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int ob = codec.dequeueOutputBuffer(info, 10000);
                if (ob >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(ob);
                    if (buf != null && info.size > 0) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        java.nio.ShortBuffer sb = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                        int frames = sb.remaining() / channels;
                        for (int fr = 0; fr < frames && n < maxOut; fr++) {
                            int sum = 0;
                            for (int c = 0; c < channels; c++) sum += sb.get(fr * channels + c);
                            short mono = (short) (sum / channels);
                            acc += 1.0;
                            while (acc >= step && n < maxOut) { // простое приведение частоты к 48 кГц
                                out[n++] = mono;
                                acc -= step;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(ob, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                } else if (ob == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat nf = codec.getOutputFormat();
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    step = (double) srcRate / RATE;
                }
            }
            short[] res = new short[n];
            System.arraycopy(out, 0, res, 0, n);
            return res;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Exception ignore) {
            }
            ex.release();
        }
    }

    private static void startPlayer(int from) {
        stopPlayer();
        short[] t = track;
        if (t == null) return;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack p = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min, RATE))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            player = p;
            p.play();
            new Thread(() -> {
                int i = from;
                while (player == p && i < t.length) {
                    int len = Math.min(2048, t.length - i);
                    int w = p.write(t, i, len);
                    if (w <= 0) break;
                    i += w;
                }
            }, "StarKaraoke").start();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void stopPlayer() {
        AudioTrack p = player;
        player = null;
        if (p != null) {
            try {
                p.stop();
                p.release();
            } catch (Exception ignore) {
            }
        }
    }

    private static final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - lastProcess > 600) stopPlayer(); // запись закончилась
            else AndroidUtilities.runOnUIThread(this, 300);
        }
    };

    /** Подмешивает минусовку в запись (вызывается из StarVoice.process на каждом буфере). */
    static void mix(ByteBuffer buffer, int samples) {
        short[] t = track;
        if (t == null) return;
        long now = System.currentTimeMillis();
        if (now - lastProcess > 1000) { // новая запись — музыка с начала
            pos = 0;
            startPlayer(0);
            AndroidUtilities.runOnUIThread(watchdog, 300);
        }
        lastProcess = now;
        int p = pos;
        for (int i = 0; i < samples; i++) {
            float m = p < t.length ? t[p++] / 32768f * 0.55f : 0f;
            float v = buffer.getShort(i * 2) / 32768f * 0.9f + m;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            buffer.putShort(i * 2, (short) (v * 32767));
        }
        pos = p;
    }
}
