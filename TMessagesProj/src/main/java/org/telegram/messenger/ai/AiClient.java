package org.telegram.messenger.ai;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
