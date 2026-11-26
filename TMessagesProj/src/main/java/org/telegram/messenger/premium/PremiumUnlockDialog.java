package org.telegram.messenger.premium;

import android.content.Context;
import android.widget.Toast;

import org.telegram.ui.ActionBar.AlertDialog;

/**
 * 本地付费锁解除的开关界面：
 * 用一段文字说清楚“只解本地、服务端仍旧校验”，避免误以为充值类权益真能用。
 */
public class PremiumUnlockDialog {

    public static void show(final Context context) {
        final boolean on = PremiumUnlock.isEnabled();

        StringBuilder sb = new StringBuilder();
        sb.append(on ? "当前状态：已开启" : "当前状态：已关闭");
        sb.append("\n\n开启后只放开客户端本地的付费判断：");
        sb.append("\n· 界面上的 Premium 锁、按钮置灰、试用弹窗；");
        sb.append("\n· premiumFeaturesBlocked 本地锁（例如语音转文字的试用限制、部分贴纸与图标）。");
        sb.append("\n\n完全不会伪造服务端返回，所以真正要服务器校验的权益依旧不可用：");
        sb.append("\n· 超过 4GB 的文件上传；");
        sb.append("\n· 星星、礼物、频道 Boost、订阅与充值类操作；");
        sb.append("\n· 语音转文字、翻译等实际跑在服务器的接口。");
        sb.append("\n\n这些操作失败时会由服务器报错，属正常现象。");

        new AlertDialog.Builder(context)
                .setTitle("本地解锁付费功能")
                .setMessage(sb.toString())
                .setPositiveButton("开启", (d, w) -> {
                    PremiumUnlock.setEnabled(true);
                    Toast.makeText(context, "已开启本地解锁，返回重进可看到状态", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", (d, w) -> {
                    PremiumUnlock.setEnabled(false);
                    Toast.makeText(context, "已关闭本地解锁", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("取消", null)
                .show();
    }
}
