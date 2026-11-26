package org.telegram.messenger.ai;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * 「为各功能指定模型」面板：
 * 每个功能（对话 / 朗读 / 生图 / 翻译）各挑一条模型，互不影响，并在这里回显当前生效的那一条。
 * 选择结果走 ModelStore 的 featureKey 记忆，功能侧用 getActiveForFeature 读取。
 */
public class FeatureModelDialog {

    private static final String[] KEYS = {"chat", "tts", "image", "translate"};
    private static final String[] NAMES = {"对话（询问 AI）", "朗读（云端 TTS）", "生图", "翻译"};
    private static final String[] CAPS = {
            ModelProfile.CAP_CHAT, ModelProfile.CAP_TTS, ModelProfile.CAP_IMAGE, ModelProfile.CAP_TRANSLATE
    };

    public static void show(final Context context) {
        if (context == null) {
            return;
        }
        ScrollView scroll = new ScrollView(context);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        final int pad = AndroidUtilities.dp(16);
        box.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < KEYS.length; i++) {
            final String key = KEYS[i];
            final String cap = CAPS[i];
            final String name = NAMES[i];
            TextView row = new TextView(context);
            row.setText(name + "\n当前：" + currentText(context, key, cap));
            row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            row.setTextColor(0xffffffff);
            row.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
            row.setOnClickListener(v -> pick(context, key, cap, name));
            box.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("为各功能指定模型");
        builder.setView(scroll);
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    private static String currentText(Context context, String key, String cap) {
        try {
            ModelProfile p = ModelStore.getInstance(context).getActiveForFeature(key, cap);
            if (p == null) {
                return "未指定（该能力下还没有可用模型）";
            }
            String model = TextUtils.isEmpty(p.model) ? "" : "（" + p.model + "）";
            return p.name + model;
        } catch (Throwable e) {
            return "读取失败";
        }
    }

    private static void pick(final Context context, final String key, final String cap, final String name) {
        final ModelStore store = ModelStore.getInstance(context);
        final java.util.ArrayList<ModelProfile> list = store.getByCapability(cap);
        if (list.isEmpty()) {
            AlertDialog.Builder empty = new AlertDialog.Builder(context);
            empty.setTitle("还没有可用的模型");
            empty.setMessage("「" + ModelProfile.capabilityName(cap) + "」能力下还没有模型呢。先回上一页添加一条，编辑时勾上这项能力。");
            empty.setPositiveButton("知道了", null);
            empty.show();
            return;
        }
        final String selId = store.getSelectedId(key);
        String[] items = new String[list.size() + 1];
        items[0] = "自动（用该能力下第一条可用模型）";
        int checked = 0;
        for (int i = 0; i < list.size(); i++) {
            ModelProfile p = list.get(i);
            String model = TextUtils.isEmpty(p.model) ? "" : "（" + p.model + "）";
            items[i + 1] = p.name + model;
            if (selId != null && selId.equals(p.id)) {
                checked = i + 1;
            }
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(name + "（当前：" + (checked == 0 ? "自动" : list.get(checked - 1).name) + "）");
        builder.setItems(items, (dialog, which) -> {
            if (which == 0) {
                store.setSelectedId(key, null);
                Toast.makeText(context, name + "：已改为自动选模型", Toast.LENGTH_SHORT).show();
            } else {
                ModelProfile p = list.get(which - 1);
                store.setSelectedId(key, p.id);
                Toast.makeText(context, name + "：已指定「" + p.name + "」", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}