package org.telegram.messenger.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * iMe AI 助手（界面层）：
 * 1. 询问 AI：弹输入框，把问题发给配置中心里选了 chat 能力的那条模型，弹窗显示回答；
 * 2. 生成图片：把提示词发给选了 image 能力的那条模型，弹窗显示返回的图片地址；
 * 3. 打开 AI 机器人：按需求仅占位，点击提示“暂未实现”，不做任何跳转。
 *
 * 因为本仓库工作区不含 res 目录，弹窗全部用代码构建，文案硬编码中文。
 */
public class AiAssistantDialog {

    private static final String[] MENU_ITEMS = new String[]{
            "询问 AI",
            "生成图片",
            "打开 AI机器人（暂未实现）"
    };

    private static final String ASK_SYSTEM_PROMPT =
            "你是一个乐于助人的中文 AI 助手。请用简体中文、简洁清晰地回答用户的问题，" +
                    "不要输出多余的前后缀说明。";

    public static void show(final Context context, final int account, final String sourceText) {
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("AI助手");
        builder.setItems(MENU_ITEMS, (dialog, which) -> {
            if (which == 0) {
                showAsk(context, account, sourceText);
            } else if (which == 1) {
                showImage(context, account, sourceText);
            } else {
                Toast.makeText(context, "AI机器人功能暂未实现", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /* ======================== 询问 AI ======================== */

    private static void showAsk(final Context context, final int account, final String sourceText) {
        final EditText input = new EditText(context);
        input.setHint("输入你想问 AI 的内容");
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setMinLines(3);
        input.setMaxLines(6);
        if (!TextUtils.isEmpty(sourceText)) {
            input.setText(sourceText);
            input.setSelection(input.getText().length());
        }

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        box.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("询问 AI");
        builder.setView(scroll);
        builder.setPositiveButton("发送", (dialog, which) -> {
            CharSequence cs = input.getText();
            final String question = cs == null ? "" : cs.toString().trim();
            if (question.length() == 0) {
                Toast.makeText(context, "请先输入内容", Toast.LENGTH_SHORT).show();
                return;
            }
            final AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
            progress.setMessage("AI 正在思考…");
            progress.setCancelable(true);
            progress.show();
            AiClient.chatAsync(account, ASK_SYSTEM_PROMPT, question, new AiClient.Callback() {
                @Override
                public void onResult(final String value) {
                    AndroidUtilities.runOnUIThread(() -> {
                        dismissQuietly(progress);
                        showResult(context, "AI 回答", value);
                    });
                }

                @Override
                public void onError(final String reason) {
                    AndroidUtilities.runOnUIThread(() -> {
                        dismissQuietly(progress);
                        Toast.makeText(context, explain(reason), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /* ======================== 生成图片 ======================== */

    private static void showImage(final Context context, final int account, final String sourceText) {
        final EditText input = new EditText(context);
        input.setHint("输入画面描述，例如：一只看书的橘猫");
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setMinLines(2);
        input.setMaxLines(6);
        if (!TextUtils.isEmpty(sourceText)) {
            input.setText(sourceText);
            input.setSelection(input.getText().length());
        }

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        box.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("生成图片");
        builder.setView(scroll);
        builder.setPositiveButton("生成", (dialog, which) -> {
            CharSequence cs = input.getText();
            final String prompt = cs == null ? "" : cs.toString().trim();
            if (prompt.length() == 0) {
                Toast.makeText(context, "请先输入画面描述", Toast.LENGTH_SHORT).show();
                return;
            }
            final AlertDialog progress = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
            progress.setMessage("AI 正在作画…");
            progress.setCancelable(true);
            progress.show();
            AiClient.imageAsync(account, prompt, new AiClient.Callback() {
                @Override
                public void onResult(final String value) {
                    AndroidUtilities.runOnUIThread(() -> {
                        dismissQuietly(progress);
                        showResult(context, "生成结果", value);
                    });
                }

                @Override
                public void onError(final String reason) {
                    AndroidUtilities.runOnUIThread(() -> {
                        dismissQuietly(progress);
                        Toast.makeText(context, explain(reason), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /* ======================== 公共 ======================== */

    private static void showResult(final Context context, final String title, final String value) {
        if (context == null) {
            return;
        }
        final String content = TextUtils.isEmpty(value) ? "（模型没有返回内容）" : value;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setPositiveButton("关闭", null);
        builder.setNeutralButton("复制", (dialog, which) -> copy(context, content));
        builder.show();
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("ai_result", text));
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private static void dismissQuietly(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        try {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Throwable ignore) {
        }
    }

    private static String explain(String reason) {
        if (reason == null) {
            return "请求失败";
        }
        switch (reason) {
            case "no_model":
                return "还没配置可用模型，请到「AI 模型配置中心」添加并选中";
            case "empty_result":
                return "模型没有返回内容";
            case "exception":
                return "请求异常，请检查网络与模型配置";
            default:
                if (reason.startsWith("http_")) {
                    return "接口返回错误：" + reason.substring(5);
                }
                return "请求失败：" + reason;
        }
    }
}
