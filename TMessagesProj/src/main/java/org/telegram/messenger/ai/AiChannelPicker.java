package org.telegram.messenger.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import java.util.ArrayList;

/**
 * AI 页面的「翻频道」面板（v4 第五件）。
 *
 * 列出我加入的频道（已按 AI 权限过滤），顶上有个输入框按名字过滤：
 * 输入即筛、改词即重筛，用不惯的时候清空就是全部。
 * 列表里每个频道后面会标「可读可发 / 只读 / 已拉黑」，方便一眼看出权限状态。
 */
public class AiChannelPicker {

    public interface OnPick {
        void onPick(long dialogId);
    }

    private static final int C_BG = 0xff17212b;
    private static final int C_BAR = 0xff1c2733;
    private static final int C_INPUT = 0xff0e1621;
    private static final int C_ROW = 0xff202b36;
    private static final int C_TEXT = 0xffffffff;
    private static final int C_HINT = 0xff7d8e9f;
    private static final int C_OK = 0xff6ab7ff;
    private static final int C_BAD = 0xffff8a80;

    private AiChannelPicker() {
    }

    public static void show(final Context context, final int account, final OnPick onPick) {
        if (context == null || onPick == null) {
            return;
        }
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int pad = AndroidUtilities.dp(14);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // 顶栏
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(C_BAR);
        bar.setPadding(pad, AndroidUtilities.dp(10), pad, AndroidUtilities.dp(10));

        TextView title = new TextView(context);
        title.setText("选择频道");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView perm = new TextView(context);
        perm.setText("权限");
        perm.setTextColor(C_HINT);
        perm.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        perm.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        perm.setOnClickListener(v -> AiPermDialog.show(context, () -> {
            // 权限改完，把选择器重开一遍，列表用的就是新权限
            dialog.dismiss();
            show(context, account, onPick);
        }));
        bar.addView(perm);

        TextView close = new TextView(context);
        close.setText("关闭");
        close.setTextColor(C_HINT);
        close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        close.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        close.setOnClickListener(v -> dialog.dismiss());
        bar.addView(close);

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 过滤框
        final EditText filter = new EditText(context);
        filter.setHint("搜频道名（换个词就是重搜）");
        filter.setHintTextColor(C_HINT);
        filter.setTextColor(C_TEXT);
        filter.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        filter.setSingleLine(true);
        filter.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        GradientDrawable fBg = new GradientDrawable();
        fBg.setColor(C_INPUT);
        fBg.setCornerRadius(AndroidUtilities.dp(16));
        filter.setBackground(fBg);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp.leftMargin = pad;
        fLp.rightMargin = pad;
        fLp.topMargin = pad;
        root.addView(filter, fLp);

        final ScrollView scroll = new ScrollView(context);
        final LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, AndroidUtilities.dp(8), pad, pad);
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final ArrayList<AiChannelTools.Chan> all =
                AiChannelTools.listChannels(context, account);

        final Runnable rebuild = () -> {
            list.removeAllViews();
            String key = filter.getText() == null ? "" : filter.getText().toString().trim();
            // 「不使用频道」永远排第一
            list.addView(makeRow(context, "不使用频道（纯聊天，不碰任何频道）", C_HINT,
                    v -> {
                        dialog.dismiss();
                        onPick.onPick(0);
                    }), rowLp());
            if (all.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("没读到频道列表。可能是频道列表还没加载出来——先回聊天列表滑一下，再进来试试。");
                empty.setTextColor(C_HINT);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                empty.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(14),
                        AndroidUtilities.dp(6), AndroidUtilities.dp(6));
                list.addView(empty);
                return;
            }
            int shown = 0;
            for (final AiChannelTools.Chan c : all) {
                if (c == null) {
                    continue;
                }
                if (!TextUtils.isEmpty(key) && (c.title == null
                        || !c.title.toLowerCase().contains(key.toLowerCase()))) {
                    continue;
                }
                shown++;
                String status = c.canSend ? " · 可读可发" : " · 只读";
                int color = c.canSend ? C_OK : C_BAD;
                list.addView(makeRow(context, c.title + status, color, v -> {
                    if (!c.canSend) {
                        Toast.makeText(context, "这个频道 AI 只能读，不能发（权限里被限了）",
                                Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                    onPick.onPick(c.id);
                }), rowLp());
            }
            if (shown == 0) {
                TextView none = new TextView(context);
                none.setText(TextUtils.isEmpty(key)
                        ? "没有可用频道。可能全被权限拦了，点右上「权限」看看。"
                        : "没匹配到「" + key + "」的频道，换个词试试。");
                none.setTextColor(C_HINT);
                none.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                none.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(14),
                        AndroidUtilities.dp(6), AndroidUtilities.dp(6));
                list.addView(none);
            }
        };

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rebuild.run();
            }
        });

        rebuild.run();
        dialog.setContentView(root);
        dialog.show();
        // 注意：必须在 show() 之后再设窗口尺寸，否则会被 Dialog 内部重新覆盖
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(8);
        return lp;
    }

    private static TextView makeRow(Context context, String text, int color,
                                    View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setText(text == null ? "" : text);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13),
                AndroidUtilities.dp(14), AndroidUtilities.dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_ROW);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        tv.setBackground(bg);
        tv.setOnClickListener(click);
        tv.setClickable(true);
        return tv;
    }
}