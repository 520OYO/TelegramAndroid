package org.telegram.messenger.ai;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * AI 总开关。
 * 关掉之后：聊天长按菜单里的 AI 项不显示，AI 对话页与询问/生图入口一律拒绝打开。
 * 只影响 AI 相关入口，不影响翻译、朗读等其它功能。
 */
public class AiFlags {

    private static final String PREFS = "ai_main";
    private static final String KEY_ENABLED = "ai_enabled";

    public static boolean isEnabled(Context context) {
        try {
            Context c = context == null ? ApplicationLoader.applicationContext : context;
            if (c == null) {
                return true;
            }
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
        } catch (Throwable e) {
            FileLog.e(e);
            return true;
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        try {
            Context c = context == null ? ApplicationLoader.applicationContext : context;
            if (c == null) {
                return;
            }
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}