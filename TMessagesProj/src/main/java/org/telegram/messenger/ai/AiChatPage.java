package org.telegram.messenger.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;

/**
 * AI 对话独立页面（全屏）：不再是「问一句弹一次」的小弹窗，而是一个可以连续追问的对话页。
 * 入口：设置里「AI 对话」、以及聊天长按菜单的「询问 AI」（带上下文预填）。
 * 用法：AiChatPage.show(context, account, 预填问题)；预填问题为 null 时就是纯空对话。
 */
public class AiChatPage {

    private static final String SYSTEM_PROMPT = "你是一个中文 AI 助手，回答简洁、准确、直给结论；"
            + "涉及代码时给出可直接复制的完整代码；不确定的事情直接说不确定，不要编造。";

    /** 会话历史：{role, text}，role 为 "u"（我问）/ "a"（AI 答）。跨次打开保留，直到点「清空」。 */
    private static final java.util.ArrayList<String[]> HISTORY = new java.util.ArrayList<>();

    private static final int C_BG = 0xff17212b;
    private static final int C_BAR = 0xff1c2733;
    private static final int C_INPUT = 0xff0e1621;
    private static final int C_USER = 0xff2b5278;
    private static final int C_AI = 0xff202b36;
    private static final int C_TEXT = 0xffffffff;
    private static final int C_HINT = 0xff7d8e9f;

    public static void show(final Context context, final int account, final String presetQuestion) {
        if (context == null) {
            return;
        }
        if (!AiFlags.isEnabled(context)) {
            Toast.makeText(context, "AI 功能总开关是关着的，先去设置里打开哦", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // ── 顶栏：标题 + 清空 + 关闭 ──
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_BAR);
        int pad = AndroidUtilities.dp(12);
        bar.setPadding(pad, AndroidUtilities.dp(10), pad, AndroidUtilities.dp(10));

        TextView title = new TextView(context);
        title.setText("AI 对话");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clear = new TextView(context);
        clear.setText("清空");
        clear.setTextColor(C_HINT);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        clear.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6),
                AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        clear.setOnClickListener(v -> {
            HISTORY.clear();
            Toast.makeText(context, "对话已清空", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            show(context, account, null);
        });
        bar.addView(clear);

        TextView close = new TextView(context);
        close.setText("关闭");
        close.setTextColor(C_HINT);
        close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        close.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 消息区 ──
        final ScrollView scroll = new ScrollView(context);
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (HISTORY.isEmpty()) {
            addBubble(context, list, "a", "我是这条消息里的 AI 助手～ 有什么想问的直接说，可以连续追问。");
        } else {
            for (String[] h : HISTORY) {
                addBubble(context, list, h[0], h[1]);
            }
        }

        // ── 输入区 ──
        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackgroundColor(C_BAR);
        inputRow.setPadding(pad, AndroidUtilities.dp(8), pad, AndroidUtilities.dp(8));

        final EditText input = new EditText(context);
        input.setHint("问点什么…");
        input.setHintTextColor(C_HINT);
        input.setTextColor(C_TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(C_INPUT);
        inBg.setCornerRadius(AndroidUtilities.dp(18));
        input.setBackground(inBg);
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView send = new TextView(context);
        send.setText("发送");
        send.setTextColor(0xffffffff);
        send.setGravity(Gravity.CENTER);
        send.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        send.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setColor(C_USER);
        sendBg.setCornerRadius(AndroidUtilities.dp(18));
        send.setBackground(sendBg);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sendLp.leftMargin = AndroidUtilities.dp(8);
        inputRow.addView(send, sendLp);

        root.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        final Runnable doSend = () -> {
            final String q = input.getText() == null ? "" : input.getText().toString().trim();
            if (q.length() == 0) {
                return;
            }
            if (!AiFlags.isEnabled(context)) {
                Toast.makeText(context, "AI 功能总开关是关着的", Toast.LENGTH_SHORT).show();
                return;
            }
            input.setText("");
            HISTORY.add(new String[]{"u", q});
            addBubble(context, list, "u", q);

            final TextView[] pending = addBubble(context, list, "a", "思考中…");
            send.setEnabled(false);
            send.setAlpha(0.5f);
            scrollDown(scroll);

            AiClient.chatAsync(account, SYSTEM_PROMPT, q, new AiClient.Callback() {
                @Override
                public void onResult(final String value) {
                    AndroidUtilities.runOnUIThread(() -> {
                        String text = value == null ? "" : value.trim();
                        pending[0].setText(text);
                        HISTORY.add(new String[]{"a", text});
                        send.setEnabled(true);
                        send.setAlpha(1f);
                        scrollDown(scroll);
                    });
                }

                @Override
                public void onError(final String reason) {
                    AndroidUtilities.runOnUIThread(() -> {
                        String text = "❌ " + AiClient.explain(reason);
                        pending[0].setText(text);
                        HISTORY.add(new String[]{"a", text});
                        send.setEnabled(true);
                        send.setAlpha(1f);
                        scrollDown(scroll);
                    });
                }
            });
        };

        send.setOnClickListener(v -> doSend.run());

        dialog.show();
        if (!TextUtils.isEmpty(presetQuestion)) {
            input.setText(presetQuestion);
            input.setSelection(input.getText().length());
        }
        input.requestFocus();
        scrollDown(scroll);
    }

    /** 追加一个气泡，返回承载文字的 TextView（方便把「思考中…」原地改成结果）。 */
    private static TextView[] addBubble(Context context, LinearLayout list, String role, String text) {
        boolean user = "u".equals(role);
        TextView tv = new TextView(context);
        tv.setText(text == null ? "" : text);
        tv.setTextColor(C_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setTextIsSelectable(true);
        int pad = AndroidUtilities.dp(12);
        tv.setPadding(pad, AndroidUtilities.dp(9), pad, AndroidUtilities.dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(user ? C_USER : C_AI);
        bg.setCornerRadius(AndroidUtilities.dp(14));
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(8);
        if (user) {
            lp.gravity = Gravity.END;
            lp.leftMargin = AndroidUtilities.dp(48);
        } else {
            lp.gravity = Gravity.START;
            lp.rightMargin = AndroidUtilities.dp(48);
        }
        list.addView(tv, lp);
        return new TextView[]{tv};
    }

    private static void scrollDown(final ScrollView scroll) {
        if (scroll == null) {
            return;
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
}
