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
        final String want = getSelectedEngineId(context);
        if (engine != null && want.equals(engine.getId())) {
            return engine;
        }
        if (engine != null) {
            try {
                engine.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            engine = null;
        }
        if (CloudTtsEngine.ID.equals(want)) {
            engine = new CloudTtsEngine();
        } else {
            engine = new SystemTtsEngine(context);
        }
        return engine;
    }

    /** 设置页读写的引擎标识：system=系统本地，cloud=云端模型 */
    public static String getSelectedEngineId(Context context) {
        try {
            if (context == null) context = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (context == null) return SystemTtsEngine.ID;
            final String v = context.getSharedPreferences("ai_tts", Context.MODE_PRIVATE)
                    .getString("tts_engine", SystemTtsEngine.ID);
            return (v == null || v.length() == 0) ? SystemTtsEngine.ID : v;
        } catch (Throwable e) {
            FileLog.e(e);
            return SystemTtsEngine.ID;
        }
    }

    /** 换引擎：会先停掉当前朗读并释放旧引擎 */
    public static void setSelectedEngineId(Context context, String id) {
        try {
            if (context == null) context = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (context == null) return;
            final String v = (id == null || id.length() == 0) ? SystemTtsEngine.ID : id;
            context.getSharedPreferences("ai_tts", Context.MODE_PRIVATE)
                    .edit().putString("tts_engine", v).apply();
            final ReadAloudController c = getInstance();
            c.stop();
            c.release();
        } catch (Throwable e) {
            FileLog.e(e);
        }
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
