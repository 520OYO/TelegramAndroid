package org.telegram.messenger.ai;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 外文消息自动译成中文：只改本机内存里的显示译文，
 * 不动 messageOwner.message，不调 updateMessageCustomParams，因此不改原文、不写库。
 */
public class AutoTranslate {

    private static final HashSet<String> inFlight = new HashSet<>();
    private static final int MAX_CONCURRENT = 3;
    private static boolean enabled = true;

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void maybeTranslate(final MessageObject obj) {
        try {
            if (!enabled || obj == null || obj.messageOwner == null) {
                return;
            }
            if (obj.translated || obj.localTranslation != null) {
                return;
            }
            final CharSequence text = obj.messageText;
            if (TextUtils.isEmpty(text)) {
                return;
            }
            final String src = text.toString().trim();
            if (src.length() < 2 || src.length() > 4000) {
                return;
            }
            if (!LocalTranslate.looksForeign(src)) {
                return;
            }
            final String cached = LocalTranslate.getCached(src);
            if (cached != null) {
                apply(obj, cached);
                return;
            }
            final String key = obj.getDialogId() + "_" + obj.getId();
            synchronized (inFlight) {
                if (inFlight.contains(key) || inFlight.size() >= MAX_CONCURRENT) {
                    return;
                }
                inFlight.add(key);
            }
            LocalTranslate.translateAsync(obj.currentAccount, src, new LocalTranslate.Callback() {
                @Override
                public void onResult(final String zh) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (inFlight) {
                                inFlight.remove(key);
                            }
                            apply(obj, zh);
                        }
                    });
                }

                @Override
                public void onError(final String reason) {
                    synchronized (inFlight) {
                        inFlight.remove(key);
                    }
                }
            });
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    private static void apply(MessageObject obj, String zh) {
        if (obj == null || TextUtils.isEmpty(zh) || obj.messageOwner == null) {
            return;
        }
        if (obj.translated || obj.localTranslation != null) {
            return;
        }
        final TLRPC.TL_textWithEntities translated = new TLRPC.TL_textWithEntities();
        translated.text = zh;
        translated.entities = new ArrayList<>();
        obj.localTranslation = translated;
        obj.updateTranslation(true);
        NotificationCenter.getInstance(obj.currentAccount).postNotificationName(NotificationCenter.messageTranslated, obj, false);
    }
}
