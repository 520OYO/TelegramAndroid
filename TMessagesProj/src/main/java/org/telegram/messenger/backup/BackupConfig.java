package org.telegram.messenger.backup;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * 频道备份配置：目录、目标频道、日志频道、黑白名单、间隔与重试。
 * 全部存 SharedPreferences，不依赖任何 res 资源。
 */
public class BackupConfig {

    private static final String PREFS_NAME = "channel_backup_cfg";

    private static final String KEY_DIR_PHOTO = "dir_photo";
    private static final String KEY_DIR_VIDEO = "dir_video";
    private static final String KEY_DIR_MUSIC = "dir_music";
    private static final String KEY_TARGET_CHANNEL = "target_channel";
    private static final String KEY_LOG_CHANNEL = "log_channel";
    private static final String KEY_WHITELIST = "whitelist";
    private static final String KEY_BLACKLIST = "blacklist";
    private static final String KEY_INTERVAL = "interval_ms";
    private static final String KEY_MAX_RETRY = "max_retry";
    private static final String KEY_BATCH_LIMIT = "batch_limit";

    public static final int DEFAULT_INTERVAL_MS = 1500;
    public static final int DEFAULT_MAX_RETRY = 3;
    public static final int DEFAULT_BATCH_LIMIT = 200;

    private static volatile BackupConfig instance;

    private final SharedPreferences prefs;

    private BackupConfig(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public static BackupConfig getInstance() {
        BackupConfig local = instance;
        if (local == null) {
            synchronized (BackupConfig.class) {
                local = instance;
                if (local == null) {
                    SharedPreferences p = null;
                    try {
                        Context ctx = ApplicationLoader.applicationContext;
                        if (ctx != null) {
                            p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        }
                    } catch (Throwable ignore) {
                    }
                    local = new BackupConfig(p);
                    instance = local;
                }
            }
        }
        return local;
    }

    private String getString(String key, String def) {
        if (prefs == null) {
            return def;
        }
        try {
            return prefs.getString(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private void putString(String key, String value) {
        if (prefs == null) {
            return;
        }
        try {
            prefs.edit().putString(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private long getLong(String key, long def) {
        if (prefs == null) {
            return def;
        }
        try {
            return prefs.getLong(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private void putLong(String key, long value) {
        if (prefs == null) {
            return;
        }
        try {
            prefs.edit().putLong(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private int getInt(String key, int def) {
        if (prefs == null) {
            return def;
        }
        try {
            return prefs.getInt(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private void putInt(String key, int value) {
        if (prefs == null) {
            return;
        }
        try {
            prefs.edit().putInt(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    public String getDirPhoto() {
        return getString(KEY_DIR_PHOTO, "/sdcard/DCIM/Camera");
    }

    public void setDirPhoto(String v) {
        putString(KEY_DIR_PHOTO, v == null ? "" : v.trim());
    }

    public String getDirVideo() {
        return getString(KEY_DIR_VIDEO, "/sdcard/DCIM/Camera");
    }

    public void setDirVideo(String v) {
        putString(KEY_DIR_VIDEO, v == null ? "" : v.trim());
    }

    public String getDirMusic() {
        return getString(KEY_DIR_MUSIC, "/sdcard/Music");
    }

    public void setDirMusic(String v) {
        putString(KEY_DIR_MUSIC, v == null ? "" : v.trim());
    }

    public long getTargetChannel() {
        return getLong(KEY_TARGET_CHANNEL, 0L);
    }

    public void setTargetChannel(long id) {
        putLong(KEY_TARGET_CHANNEL, id);
    }

    public long getLogChannel() {
        return getLong(KEY_LOG_CHANNEL, 0L);
    }

    public void setLogChannel(long id) {
        putLong(KEY_LOG_CHANNEL, id);
    }

    public String getWhitelist() {
        return getString(KEY_WHITELIST, "");
    }

    public void setWhitelist(String v) {
        putString(KEY_WHITELIST, v == null ? "" : v.trim());
    }

    public String getBlacklist() {
        return getString(KEY_BLACKLIST, "");
    }

    public void setBlacklist(String v) {
        putString(KEY_BLACKLIST, v == null ? "" : v.trim());
    }

    public int getIntervalMs() {
        int v = getInt(KEY_INTERVAL, DEFAULT_INTERVAL_MS);
        return v < 300 ? 300 : v;
    }

    public void setIntervalMs(int v) {
        putInt(KEY_INTERVAL, v < 300 ? 300 : v);
    }

    public int getMaxRetry() {
        int v = getInt(KEY_MAX_RETRY, DEFAULT_MAX_RETRY);
        return v < 1 ? 1 : v;
    }

    public void setMaxRetry(int v) {
        putInt(KEY_MAX_RETRY, v < 1 ? 1 : v);
    }

    public int getBatchLimit() {
        int v = getInt(KEY_BATCH_LIMIT, DEFAULT_BATCH_LIMIT);
        return v < 1 ? 1 : v;
    }

    public void setBatchLimit(int v) {
        putInt(KEY_BATCH_LIMIT, v < 1 ? 1 : v);
    }

    private static boolean listContains(String csv, long id) {
        if (csv == null || csv.length() == 0) {
            return false;
        }
        String[] parts = csv.split(",");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.length() == 0) {
                continue;
            }
            try {
                if (Long.parseLong(s) == id) {
                    return true;
                }
            } catch (Throwable ignore) {
            }
        }
        return false;
    }

    /**
     * 是否允许把素材发到该频道。日志频道永远不允许作为素材目标。
     */
    public boolean isChannelAllowed(long channelId) {
        if (channelId == 0L) {
            return false;
        }
        if (channelId == getLogChannel()) {
            return false;
        }
        if (listContains(getBlacklist(), channelId)) {
            return false;
        }
        String wl = getWhitelist();
        if (wl != null && wl.length() > 0) {
            return listContains(wl, channelId);
        }
        return true;
    }
}
