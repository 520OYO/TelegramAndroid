/*
 * Fork client: 在 App 内填写 api_id / api_hash。
 * 值保存到主设置文件（mainconfig），由 BuildVars.loadRuntimeCredentials()
 * 在下次启动时覆盖编译期占位值。
 */
package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.forkgram.ApiCredentials;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineEditText;

public class ApiCredentialsActivity extends BaseFragment {

    private static final int DONE_BUTTON = 1;

    private EditTextBoldCursor idField;
    private EditTextBoldCursor hashField;
    private TextView idError;
    private TextView hashError;
    private EditTextBoldCursor lastField;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle("API Credentials");
        actionBar.setAllowOverlayTitle(true);
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == DONE_BUTTON) {
                    save();
                }
            }
        });
        actionBar.createMenu().addItemWithWidth(DONE_BUTTON, R.drawable.ic_ab_done, AndroidUtilities.dp(56), "Done");

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(16);
        content.setPadding(pad, pad, pad, pad);
        scrollView.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(context);
        info.setText("Enter the api_id and api_hash of your own Telegram application (my.telegram.org). "
                + "They are stored only on this device and override the values baked into the build. "
                + "Restart the app after saving.");
        info.setTextSize(14);
        info.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        idError = addField(content, context, "API ID",
                ApiCredentials.getApiId() > 0 ? String.valueOf(ApiCredentials.getApiId()) : "", "12345678",
                InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_NEXT);
        idField = lastField;

        hashError = addField(content, context, "API Hash",
                ApiCredentials.getApiHash(), null,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE);
        hashField = lastField;
        hashField.setTransformationMethod(PasswordTransformationMethod.getInstance());

        TextView hint = new TextView(context);
        hint.setText("Leave both fields empty and press Done to remove the override and fall back to the built-in values.");
        hint.setTextSize(13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        content.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, AndroidUtilities.dp(6), 0, 0));

        return fragmentView;
    }

    private TextView addField(LinearLayout content, Context context, String label, String value, String hint, int inputType, int imeOptions) {
        OutlineEditText outline = new OutlineEditText(context);
        outline.setHint(label);
        EditTextBoldCursor field = outline.getEditText();
        field.setText(value != null ? value : "");
        if (hint != null) {
            field.setHint(hint);
        }
        field.setInputType(inputType);
        field.setImeOptions(imeOptions);
        int pad = AndroidUtilities.dp(16);
        field.setPadding(AndroidUtilities.dp(15), pad, AndroidUtilities.dp(15), pad);
        field.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        content.addView(outline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, AndroidUtilities.dp(2)));
        lastField = field;
        TextView error = makeError(context);
        content.addView(error, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(10)));
        return error;
    }

    private TextView makeError(Context context) {
        TextView error = new TextView(context);
        error.setTextSize(13);
        error.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        error.setVisibility(View.GONE);
        return error;
    }

    private static void showError(TextView view, String message) {
        if (message == null) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(message);
            view.setVisibility(View.VISIBLE);
        }
    }

    private void save() {
        String idText = idField.getText().toString().trim();
        String hash = hashField.getText().toString().trim();

        if (idText.isEmpty() && hash.isEmpty()) {
            ApiCredentials.clear();
            toast("Override removed. Restart the app to apply.");
            finishFragment();
            return;
        }

        String idErrorMessage = null;
        String hashErrorMessage = null;
        int apiId = -1;

        if (idText.isEmpty()) {
            idErrorMessage = "Required";
        } else {
            try {
                apiId = Integer.parseInt(idText);
            } catch (Throwable ignore) {
                apiId = -1;
            }
            if (apiId <= 0) {
                idErrorMessage = "Must be a positive number";
            }
        }

        if (hash.isEmpty()) {
            hashErrorMessage = "Required";
        } else if (!ApiCredentials.isValidHash(hash)) {
            hashErrorMessage = "Must be 32 hex characters";
        }

        showError(idError, idErrorMessage);
        showError(hashError, hashErrorMessage);
        if (idErrorMessage != null || hashErrorMessage != null) {
            return;
        }

        ApiCredentials.save(apiId, hash);
        toast("Saved. Restart the app to apply.");
        finishFragment();
    }

    private void toast(String message) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }
}