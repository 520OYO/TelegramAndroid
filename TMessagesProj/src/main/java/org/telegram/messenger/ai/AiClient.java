package org.telegram.messenger.ai;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.Map;

/**
 * 通用 AI 请求客户端：对话与生图。
 * 所有模型信息全部来自「AI 模型配置中心」，不内置任何服务商。
 */
public class AiClient {

    public interface Callback {
        void onResult(String value);
        void onError(String reason);
    }

    public static void chatAsync(final int account, final String system, final String user, final Callback callback) {
        try {
            final ModelProfile profile = ModelStore.getInstance().getActiveForFeature("chat", ModelProfile.CAP_CHAT);
            if (profile == null) {
                if (callback != null) callback.onError("no_model");
                return;
            }
            final String body = buildChatBody(profile, system, user);
            run(profile, body, new Callback() {
                @Override
                public void onResult(String raw) {
                    final String text = extractChat(raw);
                    if (callback == null) return;
                    if (TextUtils.isEmpty(text)) {
                        callback.onError("empty_result");
                    } else {
                        callback.onResult(text);
                    }
                }

                @Override
                public void onError(String reason) {
                    if (callback != null) callback.onError(reason);
                }
            });
        } catch (Throwable e) {
            if (callback != null) callback.onError("exception");
        }
    }

    public static void imageAsync(final int account, final String prompt, final Callback callback) {
        try {
            final ModelProfile profile = ModelStore.getInstance().getActiveForFeature("image", ModelProfile.CAP_IMAGE);
            if (profile == null) {
                if (callback != null) callback.onError("no_model");
                return;
            }
            final String body = buildImageBody(profile, prompt);
            run(profile, body, new Callback() {
                @Override
                public void onResult(String raw) {
                    final String image = extractImage(raw);
                    if (callback == null) return;
                    if (TextUtils.isEmpty(image)) {
                        callback.onError("empty_result");
                    } else {
                        callback.onResult(image);
                    }
                }

                @Override
                public void onError(String reason) {
                    if (callback != null) callback.onError(reason);
                }
            });
        } catch (Throwable e) {
            if (callback != null) callback.onError("exception");
        }
    }

    /** 云端 TTS 回调：要么拿到音频字节，要么拿到音频 URL */
    public interface TtsCallback {
        void onAudio(byte[] data);
        void onAudioUrl(String url);
        void onError(String reason);
    }

    /** 原始响应回调：TTS 与测试连接共用 */
    public interface RawCallback {
        void onBytes(byte[] data, String contentType);
        void onText(String text);
        void onError(String reason);
    }

    /** 测试连接回调 */
    public interface TestCallback {
        void onDone(boolean ok, String message);
    }

    /** 云端 TTS：把文字交给配置好的语音模型，拿回音频 */
    public static void ttsAsync(final int account, final String text, final TtsCallback callback) {
        final ModelProfile profile = ModelStore.getInstance().getActiveForFeature("tts", ModelProfile.CAP_TTS);
        if (profile == null) {
            failTts(callback, "no_model");
            return;
        }
        if (TextUtils.isEmpty(text)) {
            failTts(callback, "empty_text");
            return;
        }
        final String body = buildTtsBody(profile, text);
        postRaw(profile, profile.getTtsFullUrl(), body, new RawCallback() {
            @Override
            public void onBytes(byte[] data, String contentType) {
                if (data != null && data.length > 512) {
                    if (callback != null) callback.onAudio(data);
                } else {
                    failTts(callback, "empty_result");
                }
            }

            @Override
            public void onText(String value) {
                final String url = extractAudioUrl(value);
                if (url != null) {
                    if (callback != null) callback.onAudioUrl(url);
                } else {
                    failTts(callback, "empty_result");
                }
            }

            @Override
            public void onError(String reason) {
                failTts(callback, reason);
            }
        });
    }

    private static void failTts(TtsCallback callback, String reason) {
        if (callback != null) callback.onError(reason);
    }

    private static String voiceOf(ModelProfile p) {
        final String v = p.ttsVoice == null ? "" : p.ttsVoice.trim();
        return v.length() == 0 ? "alloy" : v;
    }

    private static String buildTtsBody(ModelProfile p, String text) {
        final String tpl = p.ttsBodyTemplate == null ? "" : p.ttsBodyTemplate.trim();
        if (tpl.length() > 0) {
            String out = tpl;
            out = out.replace("{text}", escape(text));
            out = out.replace("{voice}", escape(voiceOf(p)));
            out = out.replace("{model}", escape(p.model));
            out = out.replace("{key}", escape(p.apiKey));
            return out;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escape(p.model)).append("\",");
        sb.append("\"input\":\"").append(escape(text)).append("\",");
        sb.append("\"voice\":\"").append(escape(voiceOf(p))).append("\",");
        sb.append("\"response_format\":\"mp3\"");
        appendExtras(sb, p.extraParams);
        sb.append("}");
        return sb.toString();
    }

    private static String extractAudioUrl(String value) {
        if (TextUtils.isEmpty(value)) return null;
        final String body = value.trim();
        if (body.startsWith("{")) {
            try {
                final JSONObject o = new JSONObject(body);
                final String[] keys = new String[]{"url", "audio_url", "audio", "result", "output"};
                for (int i = 0; i < keys.length; i++) {
                    final String v = o.optString(keys[i], "");
                    if (v != null && v.startsWith("http")) return v;
                }
                final JSONObject data = o.optJSONObject("data");
                if (data != null) {
                    for (int i = 0; i < keys.length; i++) {
                        final String v = data.optString(keys[i], "");
                        if (v != null && v.startsWith("http")) return v;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        if (body.startsWith("http")) return body;
        return null;
    }

    /** 测试连接：发一次最小请求，把结果翻成一句中文 */
    public static void testAsync(final int account, final ModelProfile p, final TestCallback callback) {
        if (p == null) {
            doneTest(callback, false, "还没有模型配置呢，先新建一条吧");
            return;
        }
        final String url = p.getFullUrl();
        if (TextUtils.isEmpty(url)) {
            doneTest(callback, false, "地址拼错了：Base URL 和请求路径都是空的");
            return;
        }
        if (TextUtils.isEmpty(p.model)) {
            doneTest(callback, false, "模型不存在：模型名没填");
            return;
        }
        final String body = buildChatBody(p, "", "连通性测试");
        postRaw(p, url, body, new RawCallback() {
            @Override
            public void onBytes(byte[] data, String contentType) {
                doneTest(callback, true, "通了：模型回话了 ✨");
            }

            @Override
            public void onText(String value) {
                doneTest(callback, true, "通了：模型回话了 ✨");
            }

            @Override
            public void onError(String reason) {
                doneTest(callback, false, explain(reason));
            }
        });
    }

    private static void doneTest(TestCallback callback, boolean ok, String message) {
        if (callback != null) callback.onDone(ok, message);
    }

    /** 把内部错误码翻成一句人话 */
    public static String explain(String reason) {
        if (reason == null || reason.length() == 0) return "未知错误";
        if (reason.startsWith("http_401") || reason.startsWith("http_403")) return "key 不对：服务端拒绝了这个 API Key";
        if (reason.startsWith("http_404")) return "地址拼错了：404 找不到接口，检查 Base URL 和请求路径";
        if (reason.startsWith("http_400")) return "模型不存在：服务端说请求不对，多半是模型名写错了";
        if (reason.startsWith("http_429")) return "太频繁了：被限流了，等一会儿再试";
        if (reason.startsWith("http_")) return "服务端出错：" + reason.substring(5);
        if ("timeout".equals(reason)) return "超时：网络太慢或者地址不通";
        if ("no_model".equals(reason)) return "还没有选模型：先在模型配置里加一条并勾上对应能力";
        if ("empty_result".equals(reason)) return "模型没回内容：服务端返回是空的";
        if ("empty_text".equals(reason)) return "这条消息没有可朗读的文字";
        if ("exception".equals(reason)) return "出错了：网络异常或者地址格式不对";
        return reason;
    }

    private static void applyAuthAndHeaders(HttpURLConnection conn, ModelProfile profile) throws Exception {
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json, audio/*");
        if (!TextUtils.isEmpty(profile.apiKey)) {
            conn.setRequestProperty("Authorization", "Bearer " + profile.apiKey);
        }
        if (profile.headers != null) {
            for (Map.Entry<String, String> e : profile.headers.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) continue;
                final String key = e.getKey().trim();
                if (key.length() == 0) continue;
                conn.setRequestProperty(key, e.getValue());
            }
        }
    }

    private static void postRaw(final ModelProfile profile, final String fullUrl, final String body, final RawCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    final URL url = new URL(fullUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    conn.setDoOutput(true);
                    applyAuthAndHeaders(conn, profile);
                    final byte[] payload = body.getBytes("UTF-8");
                    final OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.close();
                    final int code = conn.getResponseCode();
                    final InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    if (code < 200 || code >= 300) {
                        readAll(is);
                        if (callback != null) callback.onError("http_" + code);
                        return;
                    }
                    final String ctype = conn.getContentType();
                    if (ctype != null) {
                        final String low = ctype.toLowerCase();
                        if (low.contains("audio") || low.contains("mpeg") || low.contains("octet-stream")) {
                            final byte[] bytes = readBytes(is);
                            if (callback != null) callback.onBytes(bytes, ctype);
                            return;
                        }
                    }
                    final String text = readAll(is);
                    if (callback != null) callback.onText(text);
                } catch (SocketTimeoutException e) {
                    if (callback != null) callback.onError("timeout");
                } catch (Throwable e) {
                    if (callback != null) callback.onError("exception");
                } finally {
                    if (conn != null) {
                        try {
                            conn.disconnect();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }, "ai-raw").start();
    }

    private static byte[] readBytes(InputStream is) throws Exception {
        if (is == null) return new byte[0];
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return bos.toByteArray();
    }

    private static void run(final ModelProfile profile, final String body, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    final URL url = new URL(profile.getFullUrl());
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(60000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    if (!TextUtils.isEmpty(profile.apiKey)) {
                        conn.setRequestProperty("Authorization", "Bearer " + profile.apiKey);
                    }
                    if (profile.headers != null) {
                        for (Map.Entry<String, String> e : profile.headers.entrySet()) {
                            if (e.getKey() == null || e.getValue() == null) continue;
                            final String key = e.getKey().trim();
                            if (key.length() == 0) continue;
                            conn.setRequestProperty(key, e.getValue());
                        }
                    }
                    final byte[] payload = body.getBytes("UTF-8");
                    final OutputStream os = conn.getOutputStream();
                    os.write(payload);
                    os.close();
                    final int code = conn.getResponseCode();
                    final InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    final String text = readAll(is);
                    if (code < 200 || code >= 300) {
                        if (callback != null) callback.onError("http_" + code);
                        return;
                    }
                    if (callback != null) callback.onResult(text);
                } catch (Throwable e) {
                    if (callback != null) callback.onError("exception");
                } finally {
                    if (conn != null) {
                        try {
                            conn.disconnect();
                        } catch (Throwable ignore) {
                        }
                    }
                }
            }
        }, "ai-client").start();
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String buildChatBody(ModelProfile p, String system, String user) {
        final String tpl = p.bodyTemplate == null ? "" : p.bodyTemplate.trim();
        if (ModelProfile.STYLE_REST.equals(p.apiStyle) && tpl.length() > 0) {
            return fillTemplate(tpl, user, p);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escape(p.model)).append("\",\"messages\":[");
        if (!TextUtils.isEmpty(system)) {
            sb.append("{\"role\":\"system\",\"content\":\"").append(escape(system)).append("\"},");
        }
        sb.append("{\"role\":\"user\",\"content\":\"").append(escape(user)).append("\"}],");
        sb.append("\"temperature\":0.7");
        appendExtras(sb, p.extraParams);
        sb.append("}");
        return sb.toString();
    }

    private static String buildImageBody(ModelProfile p, String prompt) {
        final String tpl = p.bodyTemplate == null ? "" : p.bodyTemplate.trim();
        if (ModelProfile.STYLE_REST.equals(p.apiStyle) && tpl.length() > 0) {
            return fillTemplate(tpl, prompt, p);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(escape(p.model)).append("\",");
        sb.append("\"prompt\":\"").append(escape(prompt)).append("\",");
        sb.append("\"n\":1,\"size\":\"1024x1024\"");
        appendExtras(sb, p.extraParams);
        sb.append("}");
        return sb.toString();
    }

    private static String fillTemplate(String tpl, String text, ModelProfile p) {
        String out = tpl;
        out = out.replace("{text}", escape(text));
        out = out.replace("{prompt}", escape(text));
        out = out.replace("{model}", escape(p.model));
        out = out.replace("{key}", escape(p.apiKey));
        return out;
    }

    private static void appendExtras(StringBuilder sb, String extraParams) {
        if (TextUtils.isEmpty(extraParams)) return;
        try {
            final JSONObject o = new JSONObject(extraParams);
            final Iterator<String> it = o.keys();
            while (it.hasNext()) {
                final String k = it.next();
                sb.append(",\"").append(escape(k)).append("\":").append(o.get(k).toString());
            }
        } catch (Throwable ignore) {
        }
    }

    private static String extractChat(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        final String s = raw.trim();
        try {
            final JSONObject o = new JSONObject(s);
            if (o.has("choices")) {
                final JSONArray arr = o.optJSONArray("choices");
                if (arr != null && arr.length() > 0) {
                    final JSONObject c = arr.optJSONObject(0);
                    if (c != null) {
                        final JSONObject msg = c.optJSONObject("message");
                        if (msg != null) {
                            final Object content = msg.opt("content");
                            if (content instanceof String) {
                                final String v = ((String) content).trim();
                                if (v.length() > 0) return v;
                            } else if (content instanceof JSONArray) {
                                final StringBuilder sb = new StringBuilder();
                                final JSONArray parts = (JSONArray) content;
                                for (int i = 0; i < parts.length(); i++) {
                                    final JSONObject part = parts.optJSONObject(i);
                                    if (part != null && part.optString("text") != null) {
                                        sb.append(part.optString("text"));
                                    }
                                }
                                if (sb.length() > 0) return sb.toString().trim();
                            }
                        }
                        final String t = c.optString("text", "");
                        if (t != null && t.trim().length() > 0) return t.trim();
                    }
                }
            }
            final String[] keys = new String[]{"content", "answer", "result", "output", "text", "reply"};
            for (String k : keys) {
                if (o.has(k)) {
                    final String v = o.optString(k, "");
                    if (v != null && v.trim().length() > 0) return v.trim();
                }
            }
        } catch (Throwable ignore) {
        }
        return s;
    }

    private static String extractImage(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        final String s = raw.trim();
        try {
            final JSONObject o = new JSONObject(s);
            final JSONArray data = o.optJSONArray("data");
            if (data != null && data.length() > 0) {
                final Object first = data.opt(0);
                if (first instanceof String) {
                    return ((String) first).trim();
                }
                final JSONObject item = data.optJSONObject(0);
                if (item != null) {
                    final String u = item.optString("url", "");
                    if (u != null && u.trim().length() > 0) return u.trim();
                    final String b = item.optString("b64_json", "");
                    if (b != null && b.trim().length() > 0) return "base64:" + b.trim();
                }
            }
            final JSONArray images = o.optJSONArray("images");
            if (images != null && images.length() > 0) {
                final Object first = images.opt(0);
                if (first instanceof String) return ((String) first).trim();
                final JSONObject item = images.optJSONObject(0);
                if (item != null) {
                    final String u = item.optString("url", "");
                    if (u != null && u.trim().length() > 0) return u.trim();
                }
            }
            final String[] keys = new String[]{"url", "image", "image_url", "result", "output"};
            for (String k : keys) {
                if (o.has(k)) {
                    final String v = o.optString(k, "");
                    if (v != null && v.trim().length() > 0) return v.trim();
                }
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static String escape(String s) {
        if (s == null) return "";
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
}
