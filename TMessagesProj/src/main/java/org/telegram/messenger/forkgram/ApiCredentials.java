/*
 * Fork client: runtime API credentials.
 *
 * 作用：在 App 内的设置页填写 api_id / api_hash，保存到主设置文件（mainconfig），
 * 由 BuildVars.loadRuntimeCredentials() 在 ApplicationLoader.onCreate() 早期
 * 覆盖编译期占位值。改动后需要重启 App（native 层会缓存 apiId）。
 */
package org.telegram.messenger.forkgram;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

public class ApiCredentials {

    /** 主设置文件名，与 MessagesController#mainPreferences 使用的文件一致。 */
    public static final String PREF_FILE = "mainconfig";

    public static final String PREF_API_ID = "custom_api_id";
    public static final String PREF_API_HASH = "custom_api_hash";

    private static SharedPreferences prefs() {
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

    /** 设置页副标题文案。 */
    public static String describe() {
        if (hasCredentials()) {
            return "Configured (" + getApiId() + ")";
        }
        return "Not set";
    }
}
