package org.telegram.messenger.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置存储：全部模型配置以 JSON 数组的形式保存在 SharedPreferences 中。
 * 每个条目是一条独立的 ModelProfile，互不共享任何字段（禁止全局共用配置）。
 */
public class ModelStore {

    private static final String PREF_NAME = "ai_models";
    private static final String KEY_LIST = "profiles";
    private static final String KEY_LAST_USED_PREFIX = "last_used_";

    private static volatile ModelStore instance;

    private SharedPreferences prefsRef;
    private final ArrayList<ModelProfile> cache = new ArrayList<>();
    private boolean loaded;

    public static ModelStore getInstance() {
        ModelStore local = instance;
        if (local == null) {
            synchronized (ModelStore.class) {
                local = instance;
                if (local == null) {
                    local = new ModelStore();
                    instance = local;
                }
            }
        }
        return local;
    }

    private ModelStore() {
        // 通过 init(SharedPreferences) 注入存储，避免依赖 ApplicationLoader 的初始化时机
    }

    /**
     * 由调用方传入 Context 的 SharedPreferences 进行初始化（避免依赖 ApplicationLoader 时机）。
     */
    public static ModelStore getInstance(SharedPreferences preferences) {
        ModelStore local = getInstance();
        local.init(preferences);
        return local;
    }

    /**
     * 便捷入口：直接用 Context 取本模块私有的 SharedPreferences。
     */
    public static ModelStore getInstance(Context context) {
        return getInstance(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE));
    }

    private synchronized void init(SharedPreferences p) {
        if (loaded) return;
        this.prefsRef = p;
        load();
    }


    private synchronized void load() {
        cache.clear();
        loaded = true;
        if (prefsRef == null) return;
        String raw = prefsRef.getString(KEY_LIST, "");
        if (raw == null || raw.length() == 0) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ModelProfile p = ModelProfile.fromJson(o);
                if (p != null && p.id != null && p.id.length() > 0) {
                    cache.add(p);
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public synchronized void reload() {
        loaded = false;
        load();
    }

    public synchronized List<ModelProfile> getAll() {
        return new ArrayList<>(cache);
    }

    /**
     * 按能力筛选：朗读云端挑 tts、iMe 询问挑 chat、iMe 生图挑 image、翻译挑 translate。
     */
    public synchronized ArrayList<ModelProfile> getByCapability(String capability) {
        ArrayList<ModelProfile> out = new ArrayList<>();
        for (ModelProfile p : cache) {
            if (p.hasCapability(capability)) out.add(p);
        }
        return out;
    }

    public synchronized ModelProfile findById(String id) {
        if (id == null) return null;
        for (ModelProfile p : cache) {
            if (id.equals(p.id)) return p;
        }
        return null;
    }

    public synchronized void addOrUpdate(ModelProfile profile) {
        if (profile == null) return;
        if (profile.id == null || profile.id.length() == 0) {
            profile.id = "m" + System.currentTimeMillis();
        }
        for (int i = 0; i < cache.size(); i++) {
            if (profile.id.equals(cache.get(i).id)) {
                cache.set(i, profile);
                save();
                return;
            }
        }
        cache.add(profile);
        save();
    }

    public synchronized void remove(String id) {
        if (id == null) return;
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (id.equals(cache.get(i).id)) {
                cache.remove(i);
            }
        }
        save();
    }

    public synchronized void move(int from, int to) {
        if (from < 0 || from >= cache.size() || to < 0 || to >= cache.size() || from == to) return;
        ModelProfile p = cache.remove(from);
        cache.add(to, p);
        save();
    }

    private synchronized void save() {
        if (prefsRef == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (ModelProfile p : cache) {
                JSONObject o = p.toJson();
                if (o != null) arr.put(o);
            }
            prefsRef.edit().putString(KEY_LIST, arr.toString()).apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /* ---------------- 各功能“挑一条”的记忆 ---------------- */

    /**
     * 记忆某项功能上次选中的模型 id（功能之间彼此独立）。
     */
    public String getSelectedId(String featureKey) {
        if (prefsRef == null || featureKey == null) return null;
        String v = prefsRef.getString(KEY_LAST_USED_PREFIX + featureKey, null);
        if (v != null && findById(v) != null) return v;
        return null;
    }

    public void setSelectedId(String featureKey, String id) {
        if (prefsRef == null || featureKey == null) return;
        prefsRef.edit().putString(KEY_LAST_USED_PREFIX + featureKey, id == null ? "" : id).apply();
    }

    /**
     * 取某项功能当前生效的模型：优先用记忆的，其次取该能力下的第一条。
     */
    public ModelProfile getActiveForFeature(String featureKey, String capability) {
        String id = getSelectedId(featureKey);
        ModelProfile p = id == null ? null : findById(id);
        if (p != null && (capability == null || p.hasCapability(capability))) return p;
        ArrayList<ModelProfile> list = getByCapability(capability);
        return list.isEmpty() ? null : list.get(0);
    }
}
