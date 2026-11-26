package org.telegram.messenger.ai;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * AI 对话框的「频道手脚」（v4 第五件）。
 *
 * 干三件事：
 *   1. 翻频道：列出我加入的频道（受 AiPerm 权限过滤），供 AI 页面选择目标频道。
 *   2. 搜频道消息：走 Telegram 自己的本地搜索（MediaDataController.searchMessagesInChat），
 *      结果通过 chatSearchResultsAvailable 通知回调；搜不到时由调用方换词重搜。
 *   3. 代发：真正发消息前一律先要中文确认（确认框在 AiChatPage 里），这里只负责发和兜底校验。
 *
 * 注意：这里只发纯文字，不碰媒体；字体（entities）用 MediaDataController.getEntities 解析，
 * 保证粗体、链接之类的格式跟手打一致。
 */
public class AiChannelTools {

    /** 一个频道条目。 */
    public static class Chan {
        public final long id;
        public final String title;
        public final boolean canSend;

        public Chan(long id, String title, boolean canSend) {
            this.id = id;
            this.title = title;
            this.canSend = canSend;
        }
    }

    /** 发消息结果回调。 */
    public interface Result {
        void done(boolean ok, String msg);
    }

    /** 搜索结果回调（query 是最终真正用上的关键词）。 */
    public interface SearchResult {
        void onResult(String query, ArrayList<MessageObject> messages);
    }

    private AiChannelTools() {
    }

    /** 我加入的频道列表，已经按 AI 权限过滤；按最近活跃排序（getDialogs 本身就是这个顺序）。 */
    public static ArrayList<Chan> listChannels(Context context, int account) {
        ArrayList<Chan> out = new ArrayList<>();
        if (context == null) {
            return out;
        }
        Context app = context.getApplicationContext();
        try {
            ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(account).getDialogs(0);
            if (dialogs == null) {
                return out;
            }
            for (TLRPC.Dialog d : dialogs) {
                if (d == null || d.id >= 0) {
                    continue;
                }
                TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-d.id);
                if (chat == null || !ChatObject.isChannel(chat)) {
                    continue;
                }
                if (!AiPerm.canRead(app, d.id)) {
                    continue;
                }
                String name = chat.title;
                if (TextUtils.isEmpty(name)) {
                    name = String.valueOf(d.id);
                }
                boolean send = AiPerm.canSend(app, d.id) && ChatObject.canWriteToChat(chat);
                out.add(new Chan(d.id, name, send));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return out;
    }

    /** 频道名，拿不到就退化成 id 字符串。 */
    public static String titleOf(int account, long dialogId) {
        if (dialogId == 0) {
            return "（没选频道）";
        }
        try {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) {
                return chat.title;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return String.valueOf(dialogId);
    }

    /**
     * 发一条纯文字到频道。
     * 调用点必须先弹中文确认框，用户点「发送」才进这里；这里再做一次权限与写权限兜底。
     */
    public static void sendText(Context context, int account, long dialogId, String text, Result cb) {
        try {
            if (TextUtils.isEmpty(text)) {
                cb.done(false, "内容是空的，没发出去");
                return;
            }
            if (dialogId == 0) {
                cb.done(false, "还没选频道");
                return;
            }
            if (!AiPerm.canSend(context, dialogId)) {
                cb.done(false, "这个频道在 AI 权限里被禁止发送");
                return;
            }
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat == null) {
                cb.done(false, "找不到这个频道，可能已经退出或被删了");
                return;
            }
            if (!ChatObject.canWriteToChat(chat)) {
                cb.done(false, "你在该频道没有发言权限（只读频道）");
                return;
            }
            CharSequence[] cs = new CharSequence[]{text};
            ArrayList<TLRPC.MessageEntity> entities =
                    MediaDataController.getInstance(account).getEntities(cs, true);
            String finalText = cs[0] == null ? text : cs[0].toString();

            SendMessagesHelper.SendMessageParams params = new SendMessagesHelper.SendMessageParams();
            params.message = finalText;
            params.peer = dialogId;
            params.entities = entities;
            params.notify = true;
            SendMessagesHelper.getInstance(account).sendMessage(params);
            cb.done(true, "已发送到「" + titleOf(account, dialogId) + "」");
        } catch (Throwable e) {
            FileLog.e(e);
            cb.done(false, "发送失败：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 在频道里搜本地消息。结果通过 chatSearchResultsAvailable 回调，
     * 用自己随机生成的 guid 过滤，避免和聊天页正在进行的搜索串台；8 秒没结果就当空结果。
     */
    public static void searchAsync(final int account, final long dialogId, final String query,
                                   final SearchResult cb) {
        final ArrayList<MessageObject> empty = new ArrayList<>();
        if (TextUtils.isEmpty(query)) {
            cb.onResult(query, empty);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final int guid = -300000 - new Random().nextInt(100000);
                final boolean[] fired = new boolean[]{false};
                final NotificationCenter nc = NotificationCenter.getInstance(account);
                final NotificationCenter.NotificationCenterDelegate delegate =
                        new NotificationCenter.NotificationCenterDelegate() {
                            @Override
                            public void didReceivedNotification(int id, int acc, Object... args) {
                                if (id != NotificationCenter.chatSearchResultsAvailable) {
                                    return;
                                }
                                if (args == null || args.length == 0 || !(args[0] instanceof Integer)) {
                                    return;
                                }
                                if ((Integer) args[0] != guid) {
                                    return;
                                }
                                synchronized (fired) {
                                    if (fired[0]) {
                                        return;
                                    }
                                    fired[0] = true;
                                }
                                nc.removeObserver(this, NotificationCenter.chatSearchResultsAvailable);
                                ArrayList<MessageObject> res = new ArrayList<>();
                                try {
                                    res.addAll(MediaDataController.getInstance(account).searchResultMessages);
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                }
                                cb.onResult(query, res);
                            }
                        };
                nc.addObserver(delegate, NotificationCenter.chatSearchResultsAvailable);
                AndroidUtilities.runOnUIThread(() -> {
                    boolean first = false;
                    synchronized (fired) {
                        if (!fired[0]) {
                            fired[0] = true;
                            first = true;
                        }
                    }
                    if (!first) {
                        return;
                    }
                    nc.removeObserver(delegate, NotificationCenter.chatSearchResultsAvailable);
                    cb.onResult(query, empty);
                }, 8000);
                MediaDataController.getInstance(account).searchMessagesInChat(
                        query, dialogId, 0, guid, 0, 0, false, null, null, false, null);
            } catch (Throwable e) {
                FileLog.e(e);
                cb.onResult(query, empty);
            }
        });
    }

    /** 换词重搜用：把原关键词改成「最长的那个词」或「前 4 个字」，实在改不出来就返回 null。 */
    public static String alternateQuery(String q) {
        if (TextUtils.isEmpty(q)) {
            return null;
        }
        String s = q.replaceAll("[\\p{Punct}\\s]+", " ").trim();
        if (s.length() == 0) {
            return null;
        }
        String[] parts = s.split(" +");
        if (parts.length > 1) {
            String best = parts[0];
            for (String p : parts) {
                if (p != null && p.length() > best.length()) {
                    best = p;
                }
            }
            return best == null || best.length() == 0 ? null : best;
        }
        if (s.length() > 4) {
            return s.substring(0, 4);
        }
        return null;
    }

    /** 把搜索结果拼成一段中文上下文，喂给模型当资料。 */
    public static String buildSearchContext(String chanTitle, String query,
                                            ArrayList<MessageObject> messages) {
        StringBuilder sb = new StringBuilder();
        int total = messages == null ? 0 : messages.size();
        sb.append("下面是我在频道「").append(chanTitle).append("」里搜「")
                .append(query).append("」的结果（共 ").append(total).append(" 条，新的在前）：\n");
        int n = 0;
        if (messages != null) {
            for (MessageObject m : messages) {
                if (m == null || m.messageOwner == null) {
                    continue;
                }
                if (n >= 12) {
                    break;
                }
                n++;
                sb.append(n).append(". [").append(fmtDate(m.messageOwner.date)).append("] ");
                CharSequence t = m.messageText;
                String s = t == null ? "[非文字消息]" : t.toString().replace('\n', ' ');
                if (s.length() > 120) {
                    s = s.substring(0, 120) + "…";
                }
                sb.append(s).append('\n');
            }
        }
        sb.append("\n请只依据上面的消息回答，不要编造；如果这些消息不足以回答，就直说信息不够，")
                .append("并建议我换哪个关键词再搜。");
        return sb.toString();
    }

    private static String fmtDate(int date) {
        try {
            return new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(date * 1000L));
        } catch (Throwable e) {
            return "";
        }
    }
}