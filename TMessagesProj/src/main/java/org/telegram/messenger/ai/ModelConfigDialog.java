package org.telegram.messenger.ai;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置中心（界面层）：
 * 1. 可增删任意多条模型配置，每条独立（显示名 / 协议 / 接口风格 / 路径 / Body 模板 /
 *    BaseURL / API Key / 自定义请求头 / 模型名 / 额外参数 / 能力）；
 * 2. 各功能（朗读云端、iMe 询问、iMe 生图、外文翻译）通过 {@link #pick} 各自挑一条，
 *    互不影响，不做全局共用。
 * <p>
 * 因为本仓库当前工作区不含 res 目录，所以界面全部用代码动态构建，不依赖任何布局 XML。
 */
public class ModelConfigDialog {

    public interface PickCallback {
        void onPicked(ModelProfile profile);
    }

    /* ======================== 列表 ======================== */

    public static void showList(final Context context) {
        final ModelStore store = ModelStore.getInstance(context);
        final List<ModelProfile> all = store.getAll();
        final CharSequence[] items = new CharSequence[all.size() + 1];
        for (int i = 0; i < all.size(); i++) {
            ModelProfile p = all.get(i);
            String cap = p.getCapabilityText();
            items[i] = p.name + (TextUtils.isEmpty(cap) ? "  ·  未指定能力" : "  ·  " + cap);
        }
        items[all.size()] = "＋ 新建一条模型配置";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("模型配置中心");
        builder.setItems(items, (dialog, which) -> {
            if (which == all.size()) {
                showEditor(context, null);
            } else {
                showEditor(context, all.get(which));
            }
        });
        builder.setNegativeButton("关闭", null);
        builder.show();
    }

    /* ======================== 编辑 ======================== */

    public static void showEditor(final Context context, final ModelProfile existing) {
        final ModelStore store = ModelStore.getInstance(context);
        final ModelProfile p = existing == null ? new ModelProfile() : existing.copy();
        final boolean isNew = existing == null;

        ScrollView scroll = new ScrollView(context);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        box.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText eName = addField(context, box, "显示名", "例如：我的 GPT", p.name, false);
        final EditText eProtocol = addField(context, box, "协议", "https 或 http", p.protocol, false);
        final EditText eStyle = addField(context, box, "接口风格",
                "openai = OpenAI 兼容；rest = 自定义 REST", p.apiStyle, false);
        final EditText eBase = addField(context, box, "Base URL", "例如：https://api.openai.com", p.baseUrl, false);
        final EditText ePath = addField(context, box, "请求路径",
                "例如：/v1/chat/completions", p.path, false);
        final EditText eModel = addField(context, box, "模型名", "例如：gpt-4o-mini", p.model, false);
        final EditText eKey = addField(context, box, "API Key", "只保存在本机", p.apiKey, false);
        final EditText eHeaders = addField(context, box, "自定义请求头",
                "格式 key:value，多条用英文逗号分隔；值里可用 {key} 占位", p.getHeadersText(), false);
        final EditText eExtra = addField(context, box, "额外参数", "JSON 对象，可留空", p.extraParams, false);
        final EditText eBody = addField(context, box, "请求体模板（Body）",
                "可留空；用 {text} 表示要发送的文本", p.bodyTemplate, true);

        // 能力多选
        TextView capTitle = new TextView(context);
        capTitle.setText("能力（可多选）");
        capTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        capTitle.setTextColor(0xff8a8a8a);
        LinearLayout.LayoutParams capTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        capTitleLp.topMargin = AndroidUtilities.dp(10);
        box.addView(capTitle, capTitleLp);

        LinearLayout capRow = new LinearLayout(context);
        capRow.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(capRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final CheckBox cbChat = makeCap(context, "对话", p.hasCapability(ModelProfile.CAP_CHAT));
        final CheckBox cbTts = makeCap(context, "语音", p.hasCapability(ModelProfile.CAP_TTS));
        final CheckBox cbImage = makeCap(context, "生图", p.hasCapability(ModelProfile.CAP_IMAGE));
        final CheckBox cbTranslate = makeCap(context, "翻译", p.hasCapability(ModelProfile.CAP_TRANSLATE));
        capRow.addView(cbChat, weightParams());
        capRow.addView(cbTts, weightParams());
        capRow.addView(cbImage, weightParams());
        capRow.addView(cbTranslate, weightParams());

        if (!isNew) {
            TextView delete = new TextView(context);
            delete.setText("删除这条配置");
            delete.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            delete.setTextColor(0xffe0645a);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, AndroidUtilities.dp(18), 0, AndroidUtilities.dp(6));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = AndroidUtilities.dp(12);
            delete.setOnClickListener(v -> {
                AlertDialog.Builder confirm = new AlertDialog.Builder(context);
                confirm.setTitle("确认删除");
                confirm.setMessage("删除后无法恢复，确定要删掉「" + p.name + "」吗？");
                confirm.setPositiveButton("删除", (d, w) -> {
                    store.remove(p.id);
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show();
                });
                confirm.setNegativeButton("取消", null);
                confirm.show();
            });
            box.addView(delete, dlp);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(isNew ? "新建模型配置" : "编辑模型配置");
        builder.setView(scroll);
        builder.setPositiveButton("保存", (dialog, which) -> {
            p.name = text(eName);
            if (p.name.length() == 0) {
                p.name = "未命名模型";
            }
            String proto = text(eProtocol).toLowerCase();
            p.protocol = proto.contains("http") && !proto.contains("https") ? "http" : "https";
            String style = text(eStyle).toLowerCase();
            p.apiStyle = style.contains("rest") ? ModelProfile.STYLE_REST : ModelProfile.STYLE_OPENAI;
            p.baseUrl = text(eBase);
            p.path = text(ePath);
            p.model = text(eModel);
            p.apiKey = text(eKey);
            p.extraParams = text(eExtra);
            p.bodyTemplate = text(eBody);
            p.setHeadersText(text(eHeaders));

            p.capabilities.clear();
            if (cbChat.isChecked()) p.capabilities.add(ModelProfile.CAP_CHAT);
            if (cbTts.isChecked()) p.capabilities.add(ModelProfile.CAP_TTS);
            if (cbImage.isChecked()) p.capabilities.add(ModelProfile.CAP_IMAGE);
            if (cbTranslate.isChecked()) p.capabilities.add(ModelProfile.CAP_TRANSLATE);

            store.addOrUpdate(p);
            Toast.makeText(context, "已保存：" + p.name, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /* ======================== 各功能“挑一条” ======================== */

    /**
     * 让某项功能挑一条模型，选择结果按 featureKey 独立记忆。
     */
    public static void pick(final Context context, final String capability,
                            final String featureKey, final PickCallback callback) {
        final ModelStore store = ModelStore.getInstance(context);
        final ArrayList<ModelProfile> list = store.getByCapability(capability);
        if (list.isEmpty()) {
            AlertDialog.Builder empty = new AlertDialog.Builder(context);
            empty.setTitle("还没有可用的模型");
            empty.setMessage("「" + ModelProfile.capabilityName(capability) + "」能力下还没有配置模型呢，先去添加一条吧～");
            empty.setPositiveButton("去添加", (d, w) -> showList(context));
            empty.setNegativeButton("取消", null);
            empty.show();
            return;
        }

        final String currentId = store.getSelectedId(featureKey);
        CharSequence[] items = new CharSequence[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            ModelProfile p = list.get(i);
            items[i] = p.name + (p.id.equals(currentId) ? "（当前使用）" : "");
        }
        items[list.size()] = "管理模型配置…";

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("选择「" + ModelProfile.capabilityName(capability) + "」模型");
        builder.setItems(items, (dialog, which) -> {
            if (which == list.size()) {
                showList(context);
                return;
            }
            ModelProfile picked = list.get(which);
            store.setSelectedId(featureKey, picked.id);
            if (callback != null) callback.onPicked(picked);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /* ======================== 工具方法 ======================== */

    private static String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private static LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static CheckBox makeCap(Context context, String label, boolean checked) {
        CheckBox cb = new CheckBox(context);
        cb.setText(label);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cb.setTextColor(0xffffffff);
        cb.setChecked(checked);
        cb.setPadding(0, 0, 0, 0);
        return cb;
    }

    private static EditText addField(Context context, LinearLayout parent, String label,
                                     String hint, String value, boolean multiline) {
        TextView title = new TextView(context);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        title.setTextColor(0xff8a8a8a);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = AndroidUtilities.dp(10);
        parent.addView(title, tlp);

        EditText edit = new EditText(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        edit.setTextColor(0xffffffff);
        edit.setHintTextColor(0xff6f6f6f);
        edit.setHint(hint);
        edit.setBackground(null);
        int p = AndroidUtilities.dp(4);
        edit.setPadding(p, p, p, p);
        if (multiline) {
            edit.setSingleLine(false);
            edit.setMinLines(3);
            edit.setGravity(Gravity.TOP | Gravity.START);
        } else {
            edit.setSingleLine(true);
        }
        if (value != null) {
            edit.setText(value);
        }
        parent.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return edit;
    }
}
