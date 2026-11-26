package org.telegram.messenger.premium;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/**
 * 本地解锁开关（仅客户端判断）：
 * 只在本地把“当前账号是 Premium”这个判定改为 true，从而绕过客户端自己的 UI 限制、
 * 功能锁与 premiumFeaturesBlocked 判定；不伪造任何服务端返回值，
 * 因此真正需要服务端校验的权益（超 4GB 上传、星星、礼物、Boost、语音转文字接口等）依旧会被服务器拒绝。
 */
public class PremiumUnlock {

    private static final String PREFS_NAME = "premium_unlock";
    private static final String KEY_ENABLED = "enabled";

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        SharedPreferences p = prefs();
        if (p == null) {
            return false;
        }
        try {
            return p.getBoolean(KEY_ENABLED, false);
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p == null) {
            return;
        }
        try {
            p.edit().putBoolean(KEY_ENABLED, enabled).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
