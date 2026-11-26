package org.telegram.messenger.tts;

import android.content.Context;

import org.telegram.messenger.FileLog;

/**
 * 朗读调度器：全局单例，持有当前使用的引擎。
 * 同一时刻只允许一段朗读，新的朗读会先停掉上一段。
 */
public class ReadAloudController {

    public interface Listener {
        void onStart();

        void onDone();

        void onError(String reason);
    }

    private static volatile ReadAloudController instance;

    public static ReadAloudController getInstance() {
        if (instance == null) {
            synchronized (ReadAloudController.class) {
                if (instance == null) {
                    instance = new ReadAloudController();
                }
            }
        }
        return instance;
    }

    private TtsEngine engine;
    private Listener listener;
    private String currentText;

    private ReadAloudController() {
    }

    private TtsEngine ensureEngine(Context context) {
        if (engine == null) {
            engine = new SystemTtsEngine(context);
        }
        return engine;
    }

    public String getCurrentEngineId() {
        return engine == null ? SystemTtsEngine.ID : engine.getId();
    }

    public String getCurrentText() {
        return currentText;
    }

    public boolean isSpeaking() {
        try {
            return engine != null && engine.isSpeaking();
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    /**
     * 长按菜单使用：正在朗读则停止，否则开始朗读。
     */
    public void toggle(Context context, String text, Listener l) {
        if (isSpeaking()) {
            stop();
            currentText = null;
            listener = null;
            if (l != null) {
                l.onDone();
            }
            return;
        }
        speak(context, text, l);
    }

    public void speak(Context context, String text, Listener l) {
        try {
            stop();
            if (text == null || text.trim().length() == 0) {
                if (l != null) {
                    l.onError("empty");
                }
                return;
            }
            listener = l;
            currentText = text;
            ensureEngine(context).speak(text, new TtsEngine.Callback() {
                @Override
                public void onStart() {
                    final Listener x = listener;
                    if (x != null) {
                        x.onStart();
                    }
                }

                @Override
                public void onDone() {
                    final Listener x = listener;
                    if (x != null) {
                        x.onDone();
                    }
                }

                @Override
                public void onError(String reason) {
                    final Listener x = listener;
                    if (x != null) {
                        x.onError(reason);
                    }
                }
            });
        } catch (Throwable e) {
            FileLog.e(e);
            if (l != null) {
                l.onError("exception");
            }
        }
    }

    public void stop() {
        try {
            if (engine != null) {
                engine.stop();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public void release() {
        try {
            if (engine != null) {
                engine.release();
                engine = null;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        listener = null;
        currentText = null;
    }
}
