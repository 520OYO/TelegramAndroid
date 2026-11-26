package org.telegram.messenger.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一条模型配置。每条完全独立：协议、路径、body 模板、密钥、请求头、模型名、能力类型都各管各的。
 * 不在任何地方做全局拦截或统一格式，避免互相干扰。
 */
public class ModelProfile {

    public static final String CAP_CHAT = "chat";
    public static final String CAP_TTS = "tts";
    public static final String CAP_IMAGE = "image";
    public static final String CAP_TRANSLATE = "translate";

    public static final String STYLE_OPENAI = "openai";
    public static final String STYLE_REST = "rest";

    public String id = "";
    public String name = "";
    public String protocol = "https";
    public String apiStyle = STYLE_OPENAI;
    public String path = "/v1/chat/completions";
    public String bodyTemplate = "";
    public String baseUrl = "";
    public String apiKey = "";
    public LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    public String model = "";
    public String extraParams = "";
    public List<String> capabilities = new ArrayList<>();

    public boolean hasCapability(String cap) {
        return capabilities != null && capabilities.contains(cap);
    }

    public String getCapabilityText() {
        if (capabilities == null || capabilities.isEmpty()) {
            return "未指定";
        }
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < capabilities.size(); a++) {
            if (a > 0) {
                sb.append(',');
            }
            sb.append(capabilityName(capabilities.get(a)));
        }
        return sb.toString();
    }

    public static String capabilityName(String cap) {
        if (CAP_CHAT.equals(cap)) {
            return "对话";
        } else if (CAP_TTS.equals(cap)) {
            return "语音";
        } else if (CAP_IMAGE.equals(cap)) {
            return "生图";
        } else if (CAP_TRANSLATE.equals(cap)) {
            return "翻译";
        }
        return cap;
    }

    public String getHeadersText() {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    public void setHeadersText(String text) {
        headers = new LinkedHashMap<>();
        if (text == null) {
            return;
        }
        String[] parts = text.split(",");
        for (String p : parts) {
            if (p == null) {
                continue;
            }
            String s = p.trim();
            int idx = s.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String k = s.substring(0, idx).trim();
            String v = s.substring(idx + 1).trim();
            if (k.length() > 0) {
                headers.put(k, v);
            }
        }
    }

    public void setCapabilitiesText(String text) {
        capabilities = new ArrayList<>();
        if (text == null) {
            return;
        }
        String[] parts = text.split(",");
        for (String p : parts) {
            String s = p.trim().toLowerCase();
            if (s.length() == 0 || capabilities.contains(s)) {
                continue;
            }
            capabilities.add(s);
        }
    }

    public String getFullUrl() {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String p = path == null ? "" : path.trim();
        if (p.length() > 0 && !p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }

    public ModelProfile copy() {
        ModelProfile c = new ModelProfile();
        c.id = id;
        c.name = name;
        c.protocol = protocol;
        c.apiStyle = apiStyle;
        c.path = path;
        c.bodyTemplate = bodyTemplate;
        c.baseUrl = baseUrl;
        c.apiKey = apiKey;
        c.headers = new LinkedHashMap<>(headers);
        c.model = model;
        c.extraParams = extraParams;
        c.capabilities = new ArrayList<>(capabilities);
        return c;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("name", name);
            o.put("protocol", protocol);
            o.put("apiStyle", apiStyle);
            o.put("path", path);
            o.put("bodyTemplate", bodyTemplate);
            o.put("baseUrl", baseUrl);
            o.put("apiKey", apiKey);
            o.put("model", model);
            o.put("extraParams", extraParams);
            JSONObject h = new JSONObject();
            if (headers != null) {
                for (Map.Entry<String, String> e : headers.entrySet()) {
                    h.put(e.getKey(), e.getValue());
                }
            }
            o.put("headers", h);
            JSONArray caps = new JSONArray();
            if (capabilities != null) {
                for (String s : capabilities) {
                    caps.put(s);
                }
            }
            o.put("capabilities", caps);
        } catch (Throwable ignored) {
        }
        return o;
    }

    public static ModelProfile fromJson(JSONObject o) {
        ModelProfile p = new ModelProfile();
        if (o == null) {
            return p;
        }
        p.id = o.optString("id", "");
        p.name = o.optString("name", "");
        p.protocol = o.optString("protocol", "https");
        p.apiStyle = o.optString("apiStyle", STYLE_OPENAI);
        p.path = o.optString("path", "");
        p.bodyTemplate = o.optString("bodyTemplate", "");
        p.baseUrl = o.optString("baseUrl", "");
        p.apiKey = o.optString("apiKey", "");
        p.model = o.optString("model", "");
        p.extraParams = o.optString("extraParams", "");
        p.headers = new LinkedHashMap<>();
        JSONObject h = o.optJSONObject("headers");
        if (h != null) {
            java.util.Iterator<String> it = h.keys();
            while (it.hasNext()) {
                String k = it.next();
                p.headers.put(k, h.optString(k, ""));
            }
        }
        p.capabilities = new ArrayList<>();
        JSONArray caps = o.optJSONArray("capabilities");
        if (caps != null) {
            for (int a = 0; a < caps.length(); a++) {
                String s = caps.optString(a, "");
                if (s.length() > 0) {
                    p.capabilities.add(s);
                }
            }
        }
        return p;
    }
}
