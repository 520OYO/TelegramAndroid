package org.telegram.messenger.ai;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本机翻译引擎：使用“模型配置中心”里挑给 translate 能力的那条配置发起 HTTP 请求。
 * 译文只缓存在内存里，不写数据库。
 */
public class LocalTranslate {

    public interface Callback {
        void onResult(String text);

        void onError(String reason);
    }

    private static final int MAX_CACHE = 400;
    private static final LinkedHashMap<String, String> cache = new LinkedHashMap<String, String>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE;
        }
    };

    public static synchronized String getCached(String text) {
        return text == null ? null : cache.get(text);
    }

    public static synchronized void putCached(String key, String value) {
        if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
            cache.put(key, value);
        }
    }

    /**
     * 粗略判断一段文字是不是“外文”：汉字远少于拉丁/西里尔/日文假名/韩文字母。
     */
    public static boolean looksForeign(CharSequence cs) {
        if (cs == null) {
            return false;
        }
        int han = 0;
        int lang = 0;
        final int n = cs.length();
        for (int i = 0; i < n; i++) {
            char c = cs.charAt(i);
            if (c < 128) {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                    lang++;
                }
                continue;
            }
            if (c >= 0x4e00 && c <= 0x9fff) {
                han++;
            } else if ((c >= 0x3040 && c <= 0x30ff) || (c >= 0xac00 && c <= 0xd7af) || (c >= 0x0400 && c <= 0x04ff) || (c >= 0x00c0 && c <= 0x024f)) {
                lang++;
            }
        }
        if (lang < 3) {
            return false;
        }
        return han * 5 < lang;
    }

    public static void translateAsync(final int account, final String text, final Callback callback) {
        ModelProfile profile = null;
        try {
            profile = ModelStore.getInstance().getActiveForFeature("translate", ModelProfile.CAP_TRANSLATE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (profile == null || TextUtils.isEmpty(profile.getFullUrl())) {
            callback.onError("no_model");
            return;
        }
        final ModelProfile finalProfile = profile;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = null;
                String error = null;
                HttpURLConnection conn = null;
                try {
                    final String body = buildBody(finalProfile, text);
                    final URL url = new URL(finalProfile.getFullUrl());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(60000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    if (!TextUtils.isEmpty(finalProfile.apiKey)) {
                        conn.setRequestProperty("Authorization", "Bearer " + finalProfile.apiKey);
                    }
                    if (finalProfile.headers != null) {
                        for (Map.Entry<String, String> e : finalProfile.headers.entrySet()) {
                            if (!TextUtils.isEmpty(e.getKey()) && e.getValue() != null) {
                                conn.setRequestProperty(e.getKey(), e.getValue());
                            }
                        }
                    }
                    final OutputStream os = conn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.close();
                    final int code = conn.getResponseCode();
                    final InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    final StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        final BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        br.close();
                    }
                    final String resp = sb.toString();
                    if (code < 200 || code >= 300) {
                        error = "http_" + code;
                    } else {
                        result = extractText(resp);
                        if (TextUtils.isEmpty(result)) {
                            error = "empty_result";
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    error = "exception";
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                if (result != null) {
                    putCached(text, result);
                    callback.onResult(result);
                } else {
                    callback.onError(error == null ? "translate_failed" : error);
                }
            }
        }, "ai-local-translate").start();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    static String buildBody(ModelProfile profile, String text) throws Exception {
        if (ModelProfile.STYLE_REST.equals(profile.apiStyle) && !TextUtils.isEmpty(profile.bodyTemplate)) {
            return profile.bodyTemplate
                    .replace("{text}", escape(text))
                    .replace("{model}", escape(profile.model))
                    .replace("{key}", escape(profile.apiKey));
        }
        final JSONObject body = new JSONObject();
        body.put("model", profile.model == null ? "" : profile.model);
        final JSONArray messages = new JSONArray();
        final JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", "你是专业翻译引擎。把用户给的内容翻译成简体中文，只输出译文，不要解释、不要加引号、不要复述原文。");
        messages.put(sys);
        final JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", text);
        messages.put(user);
        body.put("messages", messages);
        body.put("temperature", 0);
        if (!TextUtils.isEmpty(profile.extraParams)) {
            try {
                final JSONObject extra = new JSONObject(profile.extraParams);
                final Iterator<String> it = extra.keys();
                while (it.hasNext()) {
                    final String k = it.next();
                    body.put(k, extra.get(k));
                }
            } catch (Throwable ignored) {
            }
        }
        return body.toString();
    }

    static String extractText(String resp) {
        if (TextUtils.isEmpty(resp)) {
            return null;
        }
        try {
            final JSONObject o = new JSONObject(resp);
            final JSONArray choices = o.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                final JSONObject c0 = choices.optJSONObject(0);
                if (c0 != null) {
                    final JSONObject msg = c0.optJSONObject("message");
                    if (msg != null) {
                        final String content = msg.optString("content", "");
                        if (!TextUtils.isEmpty(content)) {
                            return content.trim();
                        }
                    }
                    final String t = c0.optString("text", "");
                    if (!TextUtils.isEmpty(t)) {
                        return t.trim();
                    }
                }
            }
            final String[] keys = {"translated_text", "translation", "result", "output", "text", "content"};
            for (String k : keys) {
                final String v = o.optString(k, "");
                if (!TextUtils.isEmpty(v)) {
                    return v.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        final String trimmed = resp.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        return null;
    }
}
