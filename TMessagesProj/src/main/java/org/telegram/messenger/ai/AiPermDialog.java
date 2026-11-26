package org.telegram.messenger.ai;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;

/**
 * AI 页面的「频道权限」面板（v4 第五件）。
 *
 * 三个模式单选 + 白名单 / 黑名单两个输入框。
 * 名单里一行一个 id（逗号、分号、空格都能分隔），只写正数也行，会自动补成负数。
 * 备份用的日志频道不用写进来——它会被自动当成黑名单，防止 AI 把机器流水当成真人消息去读。
 *
 * 这里填的东西只存在本机 SharedPreferences，不上传、不打印、不进日志。
 */
public class AiPermDialog {

    private static final int C_BG = 0xff17212b;
    private static final int C_BAR = 0xff1c2733;
    private static final int C_INPUT = 0xff0e1621;
    private static final int C_TEXT = 0xffffffff;
    private static final int C_HINT = 0xff7d8e9f;
    private static final int C_OK = 0xff6ab7ff;

    private AiPermDialog() {
    }

    public static void show(final Context context) {
        show(context, null);
    }

    /**
     * @param onSaved 保存成功后回调（可以传 null）。用来让调用方刷新自己的列表。
     */
    public static void show(final Context context, final Runnable onSaved) {
        if (context == null) {
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
        title.setText("频道权限");
        title.setTextColor(C_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = new TextView(context);
        save.setText("保存");
        save.setTextColor(C_OK);
        save.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        save.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        bar.addView(save);

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

        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, pad);
        scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 说明
        TextView tip = new TextView(context);
        tip.setText("AI 只能碰这里允许它碰的频道。黑名单永远最优先；"
                + "备份的日志频道会自动算进黑名单，不用手填。");
        tip.setTextColor(C_HINT);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tip.setLineSpacing(AndroidUtilities.dp(2), 1f);
        body.addView(tip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 三模式单选
        final RadioGroup group = new RadioGroup(context);
        group.setOrientation(RadioGroup.VERTICAL);
        LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gLp.topMargin = AndroidUtilities.dp(10);

        final int[] modes = new int[]{AiPerm.MODE_ALL, AiPerm.MODE_WHITE, AiPerm.MODE_SILENT};
        final int cur = AiPerm.getMode(context);
        for (int i = 0; i < modes.length; i++) {
            RadioButton rb = new RadioButton(context);
            rb.setId(1000 + i);
            rb.setText(AiPerm.modeName(modes[i]));
            rb.setTextColor(C_TEXT);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            rb.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(4), AndroidUtilities.dp(8));
            rb.setChecked(modes[i] == cur);
            group.addView(rb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        body.addView(group, gLp);

        // 白名单
        TextView wLabel = new TextView(context);
        wLabel.setText("白名单（只在「只允许白名单」模式下生效）");
        wLabel.setTextColor(C_HINT);
        wLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lLp.topMargin = AndroidUtilities.dp(14);
        body.addView(wLabel, lLp);

        final EditText white = makeInput(context,
                "一行一个频道 id，例如 -1001234567890（也可以用逗号隔开）");
        white.setText(AiPerm.getWhiteText(context));
        body.addView(white, boxLp());

        // 黑名单
        TextView bLabel = new TextView(context);
        bLabel.setText("黑名单（永远不许读、不许发）");
        bLabel.setTextColor(C_HINT);
        bLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = AndroidUtilities.dp(14);
        body.addView(bLabel, bLp);

        final EditText black = makeInput(context,
                "一行一个频道 id，例如 -1001234567890");
        black.setText(AiPerm.getBlackText(context));
        body.addView(black, boxLp());

        // 底部小字：保存后怎么生效
        TextView foot = new TextView(context);
        foot.setText("保存后立刻生效。名单只存在这台手机上，不上传、不打印。");
        foot.setTextColor(C_HINT);
        foot.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        LinearLayout.LayoutParams fLp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fLp2.topMargin = AndroidUtilities.dp(14);
        body.addView(foot, fLp2);

        save.setOnClickListener(v -> {
            int mode = AiPerm.MODE_ALL;
            int checked = group.getCheckedRadioButtonId();
            for (int i = 0; i < modes.length; i++) {
                if (checked == 1000 + i) {
                    mode = modes[i];
                    break;
                }
            }
            AiPerm.setMode(context, mode);
            AiPerm.setWhiteText(context, white.getText() == null
                    ? "" : white.getText().toString());
            AiPerm.setBlackText(context, black.getText() == null
                    ? "" : black.getText().toString());
            int nw = AiPerm.parse(AiPerm.getWhiteText(context)).size();
            int nb = AiPerm.parse(AiPerm.getBlackText(context)).size();
            Toast.makeText(context, "已保存：" + AiPerm.modeName(mode)
                    + "（白名单 " + nw + " 个，黑名单 " + nb + " 个）",
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onSaved != null) {
                onSaved.run();
            }
        });

        dialog.setContentView(root);
        dialog.show();
        final Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private static LinearLayout.LayoutParams boxLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(6);
        return lp;
    }

    private static EditText makeInput(Context context, String hint) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setHintTextColor(C_HINT);
        et.setTextColor(C_TEXT);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(2);
        et.setMaxLines(5);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_INPUT);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        et.setBackground(bg);
        return et;
    }
}
