package org.telegram.messenger.tts;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.telegram.messenger.FileLog;

import java.util.Locale;

/**
 * 引擎 1：系统本地 TTS。
 * 完全不联网、不读任何密钥，开箱即用；若设备没装中文语音包则尝试英文，再不行则回调失败。
 */
public class SystemTtsEngine implements TtsEngine {

    public static final String ID = "system";

    private final Context context;
    private TextToSpeech tts;
    private boolean initializing;
    private boolean ready;
    private boolean failed;
    private boolean speaking;

    private String pendingText;
    private Callback pendingCallback;
    private Callback callback;

    public SystemTtsEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isSpeaking() {
        return speaking;
    }

    private void ensureInit() {
        if (initializing || ready || failed) {
            return;
        }
        initializing = true;
        try {
            tts = new TextToSpeech(context, status -> {
                initializing = false;
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    failed = true;
                    notifyError("no_engine");
                    return;
                }
                try {
                    int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        result = tts.setLanguage(Locale.US);
                    }
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        failed = true;
                        notifyError("no_language");
                        return;
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            speaking = true;
                            final Callback cb = callback;
                            if (cb != null) {
                                cb.onStart();
                            }
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            speaking = false;
                            final Callback cb = callback;
                            if (cb != null) {
                                cb.onDone();
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            speaking = false;
                            notifyError("speak_failed");
                        }
                    });
                    ready = true;
                    if (pendingText != null) {
                        final String text = pendingText;
                        final Callback cb = pendingCallback;
                        pendingText = null;
                        pendingCallback = null;
                        speak(text, cb);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    failed = true;
                    notifyError("exception");
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
            initializing = false;
            failed = true;
            notifyError("no_engine");
        }
    }

    private void notifyError(String reason) {
        speaking = false;
        final Callback cb = callback != null ? callback : pendingCallback;
        pendingText = null;
        pendingCallback = null;
        if (cb != null) {
            cb.onError(reason);
        }
    }

    @Override
    public void speak(String text, Callback cb) {
        callback = cb;
        if (text == null) {
            notifyError("speak_failed");
            return;
        }
        final String trimmed = text.trim();
        if (trimmed.length() == 0) {
            notifyError("speak_failed");
            return;
        }
        if (failed) {
            notifyError("no_engine");
            return;
        }
        if (!ready) {
            pendingText = trimmed;
            pendingCallback = cb;
            ensureInit();
            return;
        }
        try {
            tts.stop();
            final Bundle params = new Bundle();
            final String utteranceId = "read_aloud_" + System.currentTimeMillis();
            speaking = true;
            final int result = tts.speak(trimmed, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
            if (result != TextToSpeech.SUCCESS) {
                notifyError("speak_failed");
            }
        } catch (Throwable e) {
            FileLog.e(e);
            notifyError("exception");
        }
    }

    @Override
    public void stop() {
        speaking = false;
        pendingText = null;
        pendingCallback = null;
        if (tts != null && ready) {
            try {
                tts.stop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public void release() {
        speaking = false;
        pendingText = null;
        pendingCallback = null;
        callback = null;
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            tts = null;
        }
        ready = false;
        failed = false;
        initializing = false;
    }
}
