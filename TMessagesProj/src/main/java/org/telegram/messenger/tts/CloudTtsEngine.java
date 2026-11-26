package org.telegram.messenger.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ai.AiClient;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 引擎 2：云端 TTS。
 * 走「模型配置中心」里勾了「语音」能力的那条模型：把文字发过去，拿回音频（字节或链接）再播放。
 * 不写任何密钥，密钥只在模型配置里本机保存。
 */
public class CloudTtsEngine implements TtsEngine {

    public static final String ID = "cloud";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private File tempFile;
    private boolean speaking;
    private int generation;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isSpeaking() {
        return speaking;
    }

    @Override
    public void speak(final String text, final Callback callback) {
        stop();
        if (text == null || text.trim().length() == 0) {
            if (callback != null) callback.onError("empty_text");
            return;
        }
        final int gen = ++generation;
        AiClient.ttsAsync(0, text.trim(), new AiClient.TtsCallback() {
            @Override
            public void onAudio(final byte[] data) {
                if (gen != generation) return;
                final File f = newTempFile();
                if (f == null) {
                    if (callback != null) callback.onError("speak_failed");
                    return;
                }
                try {
                    final FileOutputStream fos = new FileOutputStream(f);
                    fos.write(data);
                    fos.close();
                } catch (Throwable e) {
                    FileLog.e(e);
                    if (callback != null) callback.onError("speak_failed");
                    return;
                }
                playLocal(gen, f, callback);
            }

            @Override
            public void onAudioUrl(final String url) {
                if (gen != generation) return;
                playUrl(gen, url, callback);
            }

            @Override
            public void onError(final String reason) {
                if (gen != generation) return;
                speaking = false;
                if (callback != null) callback.onError(reason);
            }
        });
    }

    private File newTempFile() {
        try {
            final Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return null;
            final File f = new File(ctx.getCacheDir(), "tts_" + System.currentTimeMillis() + ".mp3");
            return f;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private void playLocal(final int gen, final File file, final Callback callback) {
        handler.post(() -> {
            if (gen != generation) {
                deleteQuiet(file);
                return;
            }
            try {
                final MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                mp.setDataSource(file.getAbsolutePath());
                attach(gen, mp, file, callback);
            } catch (Throwable e) {
                FileLog.e(e);
                deleteQuiet(file);
                speaking = false;
                if (callback != null) callback.onError("speak_failed");
            }
        });
    }

    private void playUrl(final int gen, final String url, final Callback callback) {
        handler.post(() -> {
            if (gen != generation) return;
            try {
                final MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                mp.setDataSource(url);
                attach(gen, mp, null, callback);
            } catch (Throwable e) {
                FileLog.e(e);
                speaking = false;
                if (callback != null) callback.onError("speak_failed");
            }
        });
    }

    private void attach(final int gen, final MediaPlayer mp, final File file, final Callback callback) {
        player = mp;
        tempFile = file;
        mp.setOnPreparedListener(m -> {
            if (gen != generation) {
                releasePlayer();
                return;
            }
            speaking = true;
            if (callback != null) callback.onStart();
            try {
                m.start();
            } catch (Throwable e) {
                FileLog.e(e);
                speaking = false;
                if (callback != null) callback.onError("speak_failed");
            }
        });
        mp.setOnCompletionListener(m -> {
            final boolean current = gen == generation;
            speaking = false;
            releasePlayer();
            if (current && callback != null) callback.onDone();
        });
        mp.setOnErrorListener((m, what, extra) -> {
            speaking = false;
            releasePlayer();
            if (gen == generation && callback != null) callback.onError("speak_failed");
            return true;
        });
        try {
            mp.prepareAsync();
        } catch (Throwable e) {
            FileLog.e(e);
            speaking = false;
            releasePlayer();
            if (callback != null) callback.onError("speak_failed");
        }
    }

    private void deleteQuiet(File f) {
        try {
            if (f != null && f.exists()) f.delete();
        } catch (Throwable ignore) {
        }
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.reset();
                player.release();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        player = null;
        deleteQuiet(tempFile);
        tempFile = null;
    }

    @Override
    public void stop() {
        generation++;
        speaking = false;
        handler.post(this::releasePlayer);
    }

    @Override
    public void release() {
        stop();
    }
}
