/*
 * Fork client: runtime API credentials.
 *
 * 作用：在 App 内的设置页填写 api_id / api_hash，保存到主设置文件（mainconfig），
 * 由 BuildVars.loadRuntimeCredentials() 在 ApplicationLoader.onCreate() 早期
 * 覆盖编译期占位值。改动后需要重启 App（native 层会缓存 apiId）。
 */
package org.telegram.messenger.forkgram;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.MessagesController;

public class ApiCredentials {

    /** 主设置文件名，与 MessagesController#mainPreferences 使用的文件一致。 */
    public static final String PREF_FILE = "mainconfig";

    public static final String PREF_API_ID = "custom_api_id";
    public static final String PREF_API_HASH = "custom_api_hash";

    private static SharedPreferences prefs() {
        // 直接打开 "mainconfig"（与 MessagesController#mainPreferences 的 account 0 是同一个文件），
        // 避免在「首次配置页」这种极早期时机去构造 MessagesController 实例。
        try {
            android.content.Context context = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (context != null) {
                return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            }
        } catch (Throwable ignore) {

        }
        return MessagesController.getGlobalMainSettings();
    }

    /** 返回已保存的 api_id，未配置时返回 0。 */
    public static int getApiId() {
        try {
            return prefs().getInt(PREF_API_ID, 0);
        } catch (Throwable ignore) {
            return 0;
        }
    }

    /** 返回已保存的 api_hash，未配置时返回空字符串。 */
    public static String getApiHash() {
        try {
            String value = prefs().getString(PREF_API_HASH, "");
            return value == null ? "" : value.trim();
        } catch (Throwable ignore) {
            return "";
        }
    }

    /** api_hash 必须是 32 位十六进制字符串。 */
    public static boolean isValidHash(String hash) {
        if (hash == null) {
            return false;
        }
        String value = hash.trim();
        if (value.length() != 32) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasCredentials() {
        return getApiId() > 0 && isValidHash(getApiHash());
    }

    /** 保存覆盖值（同步提交，保证下次冷启动一定能读到）。 */
    public static void save(int apiId, String apiHash) {
        prefs().edit()
                .putInt(PREF_API_ID, apiId)
                .putString(PREF_API_HASH, apiHash == null ? "" : apiHash.trim())
                .commit();
    }

    /** 清除覆盖值，回落到编译期占位。 */
    public static void clear() {
        prefs().edit()
                .remove(PREF_API_ID)
                .remove(PREF_API_HASH)
                .commit();
    }

    /**
     * 保存凭据后必须重启进程才生效：BuildVars.APP_ID / APP_HASH 是静态字段，
     * 且 native 侧在启动时缓存 apiId，只重启 Activity 不够。
     * 这里用 AlarmManager 预约一次启动，再杀掉当前进程。
     */
    public static void restartApp(Context context) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        if (app == null) {
            return;
        }
        try {
            Intent intent = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                int flags = PendingIntent.FLAG_CANCEL_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pending = PendingIntent.getActivity(app, 4711, intent, flags);
                AlarmManager manager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
                if (manager != null) {
                    manager.set(AlarmManager.RTC, System.currentTimeMillis() + 400L, pending);
                }
            }
        } catch (Throwable ignore) {

        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Throwable ignore) {

            }
            System.exit(0);
        }, 250);
    }

    /** 设置页副标题文案。 */
    public static String describe() {
        if (hasCredentials()) {
            return "Configured (" + getApiId() + ")";
        }
        return "Not set";
    }
}
