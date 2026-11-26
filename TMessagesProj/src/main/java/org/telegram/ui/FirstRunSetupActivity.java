/*
 * Fork client: 首次启动 / 未配置时的 API 凭据填写页。
 *
 * 为什么需要它：登录流程开始前 tgnet 就要用 APP_ID 建连，而「App 内设置页」
 * 原本只能在登录之后进入 —— 编译期占位值必然被服务器拒绝
 * （CONNECTION_API_ID_INVALID），形成死锁。
 *
 * 这里用独立 Activity，不依赖 LaunchActivity 的 fragment 栈与主题体系，
 * 保证「还没登录」也能填 api_id / api_hash。值存进共享的 mainconfig
 * （ApiCredentials），由 BuildVars.loadRuntimeCredentials() 在下次冷启动
 * 覆盖编译期占位值，因此保存后必须重启进程。
 */
package org.telegram.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.forkgram.ApiCredentials;

public class FirstRunSetupActivity extends Activity {

    private static final int COLOR_TEXT = 0xFF1A1A1A;
    private static final int COLOR_HINT = 0xFF6E6E6E;
    private static final int COLOR_ERROR = 0xFFD93025;

    private EditText idField;
    private EditText hashField;
    private TextView errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 用系统主题，避免依赖仓库内的 Telegram 主题资源（登录前不该起整套 UI）。
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFFFFFFFF);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        content.setPadding(pad, dp(40), pad, dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("请先填 API 凭据");
        title.setTextSize(22);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title, marginBottom(0, dp(8)));

        TextView desc = new TextView(this);
        desc.setText("这个客户端需要你自己的 Telegram API 凭据（在 my.telegram.org 申请）。"
                + "凭据只保存在本机，不上传、不写进安装包。");
        desc.setTextSize(14);
        desc.setTextColor(COLOR_HINT);
        desc.setLineSpacing(dp(4), 1f);
        content.addView(desc, marginBottom(0, dp(24)));

        content.addView(makeLabel("api_id"), marginBottom(0, dp(6)));
        idField = makeInput(InputType.TYPE_CLASS_NUMBER, "例如 12345678");
        String savedId = ApiCredentials.getApiId() > 0 ? String.valueOf(ApiCredentials.getApiId()) : "";
        idField.setText(savedId);
        content.addView(idField, marginBottom(0, dp(16)));

        content.addView(makeLabel("api_hash"), marginBottom(0, dp(6)));
        hashField = makeInput(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "32 位十六进制字符");
        hashField.setText(ApiCredentials.getApiHash());
        content.addView(hashField, marginBottom(0, dp(8)));

        errorView = new TextView(this);
        errorView.setTextSize(13);
        errorView.setTextColor(COLOR_ERROR);
        errorView.setVisibility(View.GONE);
        content.addView(errorView, marginBottom(0, dp(16)));

        Button save = new Button(this);
        save.setText("保存并重启");
        save.setAllCaps(false);
        save.setOnClickListener(v -> save());
        content.addView(save, marginBottom(0, dp(18)));

        TextView footer = new TextView(this);
        footer.setText("保存后 App 会自动重启一次，然后进入登录页。之后也可以在登录页左上角的 ⚙ 随时修改。");
        footer.setTextSize(12);
        footer.setTextColor(COLOR_HINT);
        footer.setLineSpacing(dp(3), 1f);
        content.addView(footer);

        setContentView(scrollView);
    }

    private TextView makeLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(13);
        label.setTextColor(COLOR_HINT);
        return label;
    }

    private EditText makeInput(int inputType, String hint) {
        EditText field = new EditText(this);
        field.setInputType(inputType);
        field.setHint(hint);
        field.setTextSize(16);
        field.setTextColor(COLOR_TEXT);
        field.setSingleLine(true);
        field.setBackgroundColor(0xFFF2F2F2);
        int p = dp(12);
        field.setPadding(p, p, p, p);
        return field;
    }

    private LinearLayout.LayoutParams marginBottom(int start, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = bottom;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showError(String message) {
        errorView.setText(message);
        errorView.setVisibility(View.VISIBLE);
    }

    private void save() {
        String idText = idField.getText().toString().trim();
        String hash = hashField.getText().toString().trim();

        if (TextUtils.isEmpty(idText) || TextUtils.isEmpty(hash)) {
            showError("请先填 API 凭据：api_id 和 api_hash 都不能为空");
            return;
        }

        int apiId;
        try {
            apiId = Integer.parseInt(idText);
        } catch (Throwable ignore) {
            apiId = -1;
        }
        if (apiId <= 0) {
            showError("api_id 必须是不小于 1 的整数");
            return;
        }
        if (!ApiCredentials.isValidHash(hash)) {
            showError("api_hash 必须是 32 位十六进制字符");
            return;
        }

        errorView.setVisibility(View.GONE);
        ApiCredentials.save(apiId, hash);
        Toast.makeText(this, "已保存，正在重启…", Toast.LENGTH_SHORT).show();
        ApiCredentials.restartApp(this);
    }

    @Override
    public void onBackPressed() {
        // 没有有效凭据时不允许退出去「直接到登录页」，否则又会卡在死锁里。
        if (ApiCredentials.hasCredentials()) {
            super.onBackPressed();
        } else {
            Toast.makeText(this, "请先填 API 凭据", Toast.LENGTH_SHORT).show();
        }
    }
}
