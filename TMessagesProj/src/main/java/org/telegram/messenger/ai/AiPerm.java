package org.telegram.messenger.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.backup.BackupConfig;

import java.util.ArrayList;

/**
 * AI 对话框的「频道权限」（v4 第五件）。
 *
 * 一句话：AI 只能碰这里允许它碰的频道。
 *
 * 三种模式：
 *   MODE_ALL    全部频道都能读、能发（黑名单里的除外）
 *   MODE_WHITE  只有白名单里的频道能读、能发
 *   MODE_SILENT 都能读，但一律不许发（只出草稿，用户自己复制）
 *
 * 黑名单永远最优先：进了黑名单的频道既不能读也不能发。
 * 「本地文件备份的日志频道」会被自动当成黑名单——日志是机器写的流水，不是真人消息，
 * 不能让 AI 把日志当成人聊天内容去读（对应 docs/功能记录.md 里的长期备忘）。
 *
 * 名单和模式只存本机 SharedPreferences，不上传、不打印、不进日志。
 */
public class AiPerm {

    public static final int MODE_ALL = 0;
    public static final int MODE_WHITE = 1;
    public static final int MODE_SILENT = 2;

    private static final String PREFS = "ai_perm";
    private static final String K_MODE = "mode";
    private static final String K_WHITE = "white";
    private static final String K_BLACK = "black";

    private AiPerm() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getMode(Context context) {
        try {
            return prefs(context).getInt(K_MODE, MODE_ALL);
        } catch (Throwable e) {
            FileLog.e(e);
            return MODE_ALL;
        }
    }

    public static void setMode(Context context, int mode) {
        try {
            prefs(context).edit().putInt(K_MODE, mode).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static String modeName(int mode) {
        switch (mode) {
            case MODE_WHITE:
                return "只允许白名单";
            case MODE_SILENT:
                return "都能读，一律不许发";
            default:
                return "全部频道（黑名单除外）";
        }
    }

    private static String raw(Context context, String key) {
        try {
            String v = prefs(context).getString(key, "");
            return v == null ? "" : v;
        } catch (Throwable e) {
            FileLog.e(e);
            return "";
        }
    }

    public static String getWhiteText(Context context) {
        return raw(context, K_WHITE);
    }

    public static String getBlackText(Context context) {
        return raw(context, K_BLACK);
    }

    public static void setWhiteText(Context context, String text) {
        try {
            prefs(context).edit().putString(K_WHITE, text == null ? "" : text).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void setBlackText(Context context, String text) {
        try {
            prefs(context).edit().putString(K_BLACK, text == null ? "" : text).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * 把一段文本解析成频道 id 列表：支持逗号/分号/空格/换行分隔。
     * 允许只写数字部分（自动补成负数），所以填 -1001234567890 和 1001234567890 都认。
     */
    public static ArrayList<Long> parse(String text) {
        ArrayList<Long> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return out;
        }
        String[] parts = text.split("[,，;；\\s]+");
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String s = p.trim();
            if (s.length() == 0) {
                continue;
            }
            try {
                long id = Long.parseLong(s);
                if (id > 0) {
                    id = -id;
                }
                if (id != 0 && !out.contains(id)) {
                    out.add(id);
                }
            } catch (Throwable ignore) {
                // 填错了就跳过，别让输入把人卡住
            }
        }
        return out;
    }

    public static ArrayList<Long> getWhite(Context context) {
        return parse(getWhiteText(context));
    }

    public static ArrayList<Long> getBlack(Context context) {
        return parse(getBlackText(context));
    }

    /** 备份日志频道是不是这个 id（是的话自动算黑名单）。 */
    private static boolean isBackupLogChannel(long id) {
        if (id == 0) {
            return false;
        }
        try {
            BackupConfig cfg = BackupConfig.getInstance();
            return cfg != null && cfg.isLogChannel(id);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isBlacklisted(Context context, long id) {
        if (id == 0) {
            return true;
        }
        if (getBlack(context).contains(id)) {
            return true;
        }
        return isBackupLogChannel(id);
    }

    /** 能不能读这个频道的内容。 */
    public static boolean canRead(Context context, long id) {
        if (id == 0) {
            return false;
        }
        if (isBlacklisted(context, id)) {
            return false;
        }
        if (getMode(context) == MODE_WHITE) {
            return getWhite(context).contains(id);
        }
        return true;
    }

    /** 能不能往这个频道发消息（AI 代发的最后一道闸）。 */
    public static boolean canSend(Context context, long id) {
        if (id == 0) {
            return false;
        }
        if (isBlacklisted(context, id)) {
            return false;
        }
        int mode = getMode(context);
        if (mode == MODE_SILENT) {
            return false;
        }
        if (mode == MODE_WHITE) {
            return getWhite(context).contains(id);
        }
        return true;
    }

    /** 给界面用的一句话说明：这个频道现在是什么状态。 */
    public static String describe(Context context, long id) {
        if (id == 0) {
            return "没选频道";
        }
        if (isBlacklisted(context, id)) {
            return "已拉黑（不能读也不能发）";
        }
        if (!canSend(context, id)) {
            return "只读（不许发）";
        }
        return "可读可发";
    }
}
