package org.telegram.messenger.backup;

import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;

import java.io.File;
import java.util.ArrayList;

/**
 * E module uploader: serial sending with interval + retry, optional log channel.
 * Note: prepareSending* queue the upload asynchronously and give no callback,
 * so a file is recorded in the fingerprint table right after it is queued.
 * If a send really fails, the user can clear the index and rerun.
 */
public class BackupUploader {

    public interface Callback {
        void onProgress(int finished, int total, String name);
        void onFinish(int success, int failed, String message);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    private static final String MSG_NO_CHANNEL = "\u672a\u914d\u7f6e\u76ee\u6807\u9891\u9053";
    private static final String MSG_NO_FILE = "\u6ca1\u6709\u9700\u8981\u4e0a\u4f20\u7684\u65b0\u6587\u4ef6";
    private static final String MSG_CANCEL = "\u5df2\u53d6\u6d88";
    private static final String MSG_START = "\u5f00\u59cb\u4e0a\u4f20";
    private static final String MSG_DONE = "\u5b8c\u6210\uff1a\u6210\u529f ";
    private static final String MSG_BREAK = "\u4e0a\u4f20\u4e2d\u65ad";
    private static final String LOG_TAG = "[\u9891\u9053\u5907\u4efd] ";
    private static final String COMMA = "\uff0c";
    private static final String FAIL_LABEL = "\u5931\u8d25 ";

    private static volatile boolean sRunning;
    private static volatile boolean sCancel;

    private BackupUploader() {
    }

    public static boolean isRunning() {
        return sRunning;
    }

    public static void cancel() {
        sCancel = true;
    }

    public static boolean start(final int account, final boolean photos, final boolean videos, final boolean musics, final Callback cb) {
        if (sRunning) {
            return false;
        }
        sRunning = true;
        sCancel = false;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                int success = 0;
                int failed = 0;
                String msg;
                try {
                    BackupConfig cfg = BackupConfig.getInstance();
                    if (!cfg.hasAnyMaterialChannel()) {
                        finish(cb, 0, 0, MSG_NO_CHANNEL);
                        return;
                    }
                    ArrayList<BackupScanner.Item> list = BackupScanner.scan(photos, videos, musics);
                    final int total = list.size();
                    if (total == 0) {
                        finish(cb, 0, 0, MSG_NO_FILE);
                        return;
                    }
                    sendLog(account, LOG_TAG + MSG_START + " " + total);
                    int maxRetry = cfg.getMaxRetry();
                    long interval = cfg.getIntervalMs();
                    for (int i = 0; i < total; i++) {
                        if (sCancel) {
                            break;
                        }
                        BackupScanner.Item it = list.get(i);
                        // 按类型分流：照片 / 视频 / 音乐各自发到自己的频道。
                        // 日志频道和黑名单里的频道一律不发，直接算失败留给下一轮。
                        final long target = cfg.getChannelForType(it.type);
                        boolean ok = false;
                        if (target != 0L && cfg.isChannelAllowed(target)) {
                            for (int r = 0; r < maxRetry && !ok; r++) {
                                try {
                                    sendOne(account, target, it);
                                    ok = true;
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                    try {
                                        Thread.sleep(500L * (r + 1));
                                    } catch (InterruptedException ignore) {
                                    }
                                }
                            }
                        } else {
                            FileLog.e(new RuntimeException("backup: no allowed channel for type " + it.type));
                        }
                        if (ok) {
                            BackupStore.markUploaded(it.type, it.path, it.size, it.mtime);
                            BackupStore.setCursor(it.type, it.mtime);
                            success++;
                        } else {
                            failed++;
                        }
                        progress(cb, success + failed, total, new File(it.path).getName());
                        if (i + 1 < total && !sCancel) {
                            try {
                                Thread.sleep(interval);
                            } catch (InterruptedException ignore) {
                            }
                        }
                    }
                    msg = MSG_DONE + success + COMMA + FAIL_LABEL + failed;
                    if (sCancel) {
                        msg = MSG_CANCEL + COMMA + msg;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    msg = MSG_BREAK;
                }
                sendLog(account, LOG_TAG + msg);
                finish(cb, success, failed, msg);
            }
        }, "channel-backup-uploader");
        try {
            t.setPriority(Thread.MIN_PRIORITY);
        } catch (Throwable ignore) {
        }
        t.start();
        return true;
    }

    private static void sendOne(int account, long dialogId, BackupScanner.Item it) {
        // 素材一律不带说明文字：所有 caption 相关参数恒为 null，
        // 频道里只落文件本身，不夹带任何文字说明。
        AccountInstance ai = AccountInstance.getInstance(account);
        if (BackupScanner.TYPE_PHOTO.equals(it.type)) {
            SendMessagesHelper.prepareSendingPhoto(ai, it.path, null, dialogId, null, null, null, null, null, null, null, 0, null, true, 0, 0, SendMessageChatArguments.EMPTY);
        } else if (BackupScanner.TYPE_VIDEO.equals(it.type)) {
            SendMessagesHelper.prepareSendingVideo(ai, it.path, null, null, null, dialogId, null, null, null, null, null, 0, null, true, 0, 0, false, false, null, SendMessageChatArguments.EMPTY, 0, 0);
        } else {
            SendMessagesHelper.prepareSendingDocument(ai, it.path, it.path, null, null, mimeFor(it.path), dialogId, null, null, null, null, null, true, 0, null, SendMessageChatArguments.EMPTY, false);
        }
    }

    private static String mimeFor(String path) {
        String lower = path == null ? "" : path.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".opus") || lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".flac")) {
            return "audio/flac";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }
        if (lower.endsWith(".amr")) {
            return "audio/amr";
        }
        return "application/octet-stream";
    }

    private static void sendLog(int account, String text) {
        long logId = BackupConfig.getInstance().getLogChannel();
        if (logId == 0L) {
            return;
        }
        try {
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, logId));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void progress(final Callback cb, final int finished, final int total, final String name) {
        if (cb == null) {
            return;
        }
        UI.post(new Runnable() {
            @Override
            public void run() {
                try {
                    cb.onProgress(finished, total, name);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private static void finish(final Callback cb, final int success, final int failed, final String message) {
        sRunning = false;
        sCancel = false;
        if (cb == null) {
            return;
        }
        UI.post(new Runnable() {
            @Override
            public void run() {
                try {
                    cb.onFinish(success, failed, message);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        });
    }
}
