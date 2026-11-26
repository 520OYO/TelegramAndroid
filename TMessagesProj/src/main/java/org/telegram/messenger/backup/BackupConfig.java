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
    private static final String KEY_PHOTO_CHANNEL = "photo_channel";
    private static final String KEY_VIDEO_CHANNEL = "video_channel";
    private static final String KEY_MUSIC_CHANNEL = "music_channel";
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

    /* ---------- 三个素材分流频道：照片 / 视频 / 音乐各发各的 ---------- */

    public long getPhotoChannel() {
        return getLong(KEY_PHOTO_CHANNEL, 0L);
    }

    public void setPhotoChannel(long id) {
        putLong(KEY_PHOTO_CHANNEL, id);
    }

    public long getVideoChannel() {
        return getLong(KEY_VIDEO_CHANNEL, 0L);
    }

    public void setVideoChannel(long id) {
        putLong(KEY_VIDEO_CHANNEL, id);
    }

    public long getMusicChannel() {
        return getLong(KEY_MUSIC_CHANNEL, 0L);
    }

    public void setMusicChannel(long id) {
        putLong(KEY_MUSIC_CHANNEL, id);
    }

    /**
     * 按素材类型取分流频道：照片 / 视频 / 音乐分别发到各自的频道；
     * 某一类频道没单独设置时回落到「目标频道」，兼容 v3 的老配置。
     */
    public long getChannelForType(String type) {
        long id = 0L;
        if (BackupScanner.TYPE_PHOTO.equals(type)) {
            id = getPhotoChannel();
        } else if (BackupScanner.TYPE_VIDEO.equals(type)) {
            id = getVideoChannel();
        } else if (BackupScanner.TYPE_MUSIC.equals(type)) {
            id = getMusicChannel();
        }
        if (id == 0L) {
            id = getTargetChannel();
        }
        return id;
    }

    /** 三个素材频道 + 目标频道里是否至少配了一个，用于开始上传前的检查。 */
    public boolean hasAnyMaterialChannel() {
        return getPhotoChannel() != 0L
                || getVideoChannel() != 0L
                || getMusicChannel() != 0L
                || getTargetChannel() != 0L;
    }

    /** 判断某个频道是否被配置成了日志频道（日志频道永远不能当素材目标）。 */
    public boolean isLogChannel(long id) {
        return id != 0L && id == getLogChannel();
    }

    public long getLogChannel() {
        return getLong(KEY_LOG_CHANNEL, 0L);
    }

    public void setLogChannel(long id) {
        putLong(KEY_LOG_CHANNEL, id);
        if (id == 0L) {
            return;
        }
        // 日志频道默认进黑名单：一次设置，永久不会拿它当素材目标
        addToBlacklist(id);
        // 万一同一个 ID 也被填进了素材频道，直接清掉，防止素材灌进日志频道
        if (getTargetChannel() == id) {
            setTargetChannel(0L);
        }
        if (getPhotoChannel() == id) {
            setPhotoChannel(0L);
        }
        if (getVideoChannel() == id) {
            setVideoChannel(0L);
        }
        if (getMusicChannel() == id) {
            setMusicChannel(0L);
        }
    }

    /** 把某个频道 ID 追加进黑名单（幂等，已在里面就不重复加）。 */
    public void addToBlacklist(long id) {
        if (id == 0L) {
            return;
        }
        String bl = getBlacklist();
        if (listContains(bl, id)) {
            return;
        }
        String next = (bl == null || bl.length() == 0) ? String.valueOf(id) : (bl + "," + id);
        putString(KEY_BLACKLIST, next);
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
