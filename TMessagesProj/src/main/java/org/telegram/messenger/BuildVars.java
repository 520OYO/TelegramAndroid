/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.forkgram.ApiCredentials;

import java.util.Objects;

public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.DEBUG_VERSION;
    public static boolean LOGS_ENABLED = BuildConfig.DEBUG_VERSION;
    public static boolean DEBUG_PRIVATE_VERSION = BuildConfig.DEBUG_PRIVATE_VERSION;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = true;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static String BUILD_VERSION_STRING = BuildConfig.BUILD_VERSION_STRING;

    // 编译期占位凭据（Telegram 官方公开示例值）。真正的凭据在 App 内的
    // 「API 凭据」设置页填写，由 loadRuntimeCredentials() 在启动时覆盖这两个字段。
    // 未配置时保持占位值：登录会失败但不会崩溃。
    public static int APP_ID = 4;
    public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";

    // SafetyNet key for Google Identity SDK, set it to empty to disable
    public static String SAFETYNET_KEY = "AIzaSyDqt8P-7F7CPCseMkOiVRgb1LY8RN1bvH8";
    public static String PLAYSTORE_APP_URL = "https://play.google.com/store/apps/details?id=org.telegram.messenger";
    public static String HUAWEI_STORE_URL = "https://appgallery.huawei.com/app/C101184875";
    public static String GOOGLE_AUTH_CLIENT_ID = "760348033671-81kmi3pi84p11ub8hp9a1funsv0rn2p9.apps.googleusercontent.com";

    // Last.fm API constants
    public static String LASTFM_API_KEY = BuildConfig.LASTFM_API_KEY;
    public static String LASTFM_API_SECRET = BuildConfig.LASTFM_API_SECRET;
    public static String LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/";

    public static String UPDATE_CHANNEL_USERNAME = BuildConfig.UPDATE_CHANNEL_USERNAME;

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = false;

    // works only on official app ids, disable on your forks
    public static boolean SUPPORTS_PASSKEYS = true;
    // the second main tab: News, or Contacts as before them

    static {
        if (ApplicationLoader.applicationContext != null) {
            SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Context.MODE_PRIVATE);
            LOGS_ENABLED = DEBUG_VERSION || sharedPreferences.getBoolean("logsEnabled", DEBUG_VERSION);
            LOGS_ENABLED = sharedPreferences.getBoolean("logsEnabled", false);
            if (LOGS_ENABLED) {
                final Thread.UncaughtExceptionHandler pastHandler = Thread.getDefaultUncaughtExceptionHandler();
                Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                    FileLog.fatal(exception, false);
                    if (pastHandler != null) {
                        pastHandler.uncaughtException(thread, exception);
                    }
                });
            }
        }

        BUILD_VERSION_STRING = BuildConfig.VERSION_NAME;
        APP_ID = BuildConfig.APP_ID;
        APP_HASH = BuildConfig.APP_HASH;
        PLAYSTORE_APP_URL = "";
        DEBUG_VERSION = false;
        CHECK_UPDATES = (BuildConfig.CHECK_UPDATES != 0);
    }
    /**
     * 从主设置文件读取用户在 App 内填写的 api_id / api_hash，覆盖编译期占位值。
     * 必须在 ApplicationLoader.onCreate() 中、ConnectionsManager 初始化之前调用。
     * 注意：native 层会缓存 apiId，修改后需要重启进程才会生效。
     */
    public static void loadRuntimeCredentials(Context context) {
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context == null) {
            return;
        }
        try {
            SharedPreferences preferences = context.getSharedPreferences(ApiCredentials.PREF_FILE, Context.MODE_PRIVATE);
            int runtimeApiId = preferences.getInt(ApiCredentials.PREF_API_ID, 0);
            String runtimeApiHash = preferences.getString(ApiCredentials.PREF_API_HASH, "");
            if (runtimeApiId > 0 && ApiCredentials.isValidHash(runtimeApiHash)) {
                APP_ID = runtimeApiId;
                APP_HASH = runtimeApiHash.trim();
                FileLog.d("BuildVars: runtime api credentials applied, apiId = " + APP_ID);
            }
        } catch (Throwable ignore) {
            // 读取失败时保持编译期占位值，绝不因此崩溃
        }
    }

    public static int USER_ID_OWNER = BuildConfig.USER_ID_OWNER;
    public static String USER_REPO = BuildConfig.USER_REPO;

    public static boolean useInvoiceBilling() {
        return true;
    }

    /*private static boolean hasDirectCurrency() {
        if (!BillingController.getInstance().isReady() || BillingController.PREMIUM_PRODUCT_DETAILS == null) {
            return false;
        }
        for (ProductDetails.SubscriptionOfferDetails offerDetails : BillingController.PREMIUM_PRODUCT_DETAILS.getSubscriptionOfferDetails()) {
            for (ProductDetails.PricingPhase phase : offerDetails.getPricingPhases().getPricingPhaseList()) {
                for (String cur : MessagesController.getInstance(UserConfig.selectedAccount).directPaymentsCurrency) {
                    if (Objects.equals(phase.getPriceCurrencyCode(), cur)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }*/

    private static Boolean betaApp;
    public static boolean isBetaApp() {
        if (betaApp == null) {
            betaApp = ApplicationLoader.applicationContext != null && "org.telegram.messenger.beta".equals(ApplicationLoader.applicationContext.getPackageName());
        }
        return betaApp;
    }


    public static boolean isHuaweiStoreApp() {
        return ApplicationLoader.isHuaweiStoreBuild();
    }

    public static String getSmsHash() {
        return ApplicationLoader.isStandaloneBuild() ? "w0lkcmTZkKh" : (DEBUG_VERSION ? "O2P2z+/jBpJ" : "oLeq9AcOZkT");
    }
}
