package org.telegram.messenger.backup;

import android.content.Context;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * E module local index: uploaded fingerprint table + incremental cursor.
 * fingerprint = abs path + size + lastModified; all three equal => same file, skip upload.
 * Stored as plain text (one record per line) to avoid SharedPreferences bloat.
 */
public class BackupStore {

    private static final Object LOCK = new Object();
    private static final String INDEX_FILE = "channel_backup_index.txt";
    private static final String CURSOR_FILE = "channel_backup_cursor.txt";
    private static final long REWRITE_THRESHOLD = 4L * 1024L * 1024L;

    private static HashMap<String, long[]> sIndex;
    private static HashMap<String, Long> sCursor;
    private static long sIndexBytes;

    private BackupStore() {
    }

    private static File indexFile() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        return new File(ctx.getFilesDir(), INDEX_FILE);
    }

    private static File cursorFile() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        return new File(ctx.getFilesDir(), CURSOR_FILE);
    }

    private static HashMap<String, long[]> index() {
        synchronized (LOCK) {
            if (sIndex == null) {
                sIndex = new HashMap<>();
                loadIndex();
            }
            return sIndex;
        }
    }

    private static void loadIndex() {
        File f = indexFile();
        if (f == null || !f.exists()) {
            return;
        }
        sIndexBytes = f.length();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                int t1 = line.indexOf('\t');
                int t2 = line.lastIndexOf('\t');
                if (t1 <= 0 || t2 <= t1) {
                    continue;
                }
                int t3 = line.lastIndexOf('\t', t2 - 1);
                if (t3 <= t1) {
                    continue;
                }
                String path = line.substring(t1 + 1, t3);
                String type = line.substring(0, t1);
                try {
                    long size = Long.parseLong(line.substring(t3 + 1, t2));
                    long mtime = Long.parseLong(line.substring(t2 + 1));
                    sIndex.put(type + "\t" + path, new long[]{size, mtime});
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    public static boolean isUploaded(String type, String path, long size, long mtime) {
        HashMap<String, long[]> map = index();
        synchronized (LOCK) {
            long[] v = map.get(String.valueOf(type) + "\t" + String.valueOf(path));
            return v != null && v[0] == size && v[1] == mtime;
        }
    }

    public static void markUploaded(String type, String path, long size, long mtime) {
        String key = String.valueOf(type) + "\t" + String.valueOf(path);
        index();
        synchronized (LOCK) {
            sIndex.put(key, new long[]{size, mtime});
        }
        appendLine(key + "\t" + size + "\t" + mtime);
    }

    private static void appendLine(String line) {
        File f = indexFile();
        if (f == null) {
            return;
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f, true);
            fos.write((line + "\n").getBytes("UTF-8"));
            fos.flush();
            synchronized (LOCK) {
                sIndexBytes += line.length() + 1;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable ignore) {
            }
        }
        long bytes;
        synchronized (LOCK) {
            bytes = sIndexBytes;
        }
        if (bytes > REWRITE_THRESHOLD) {
            rewrite();
        }
    }

    private static void rewrite() {
        File target = indexFile();
        if (target == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (LOCK) {
            for (Map.Entry<String, long[]> en : sIndex.entrySet()) {
                long[] v = en.getValue();
                sb.append(en.getKey()).append('\t').append(v[0]).append('\t').append(v[1]).append('\n');
            }
        }
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp, false);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
            fos = null;
            if (!tmp.renameTo(target)) {
                if (target.exists()) {
                    target.delete();
                }
                tmp.renameTo(target);
            }
            synchronized (LOCK) {
                sIndexBytes = target.length();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private static HashMap<String, Long> cursors() {
        synchronized (LOCK) {
            if (sCursor == null) {
                sCursor = new HashMap<>();
                loadCursors();
            }
            return sCursor;
        }
    }

    private static void loadCursors() {
        File f = cursorFile();
        if (f == null || !f.exists()) {
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                int idx = line.lastIndexOf('\t');
                if (idx <= 0) {
                    continue;
                }
                try {
                    sCursor.put(line.substring(0, idx), Long.valueOf(Long.parseLong(line.substring(idx + 1))));
                } catch (Exception ignore) {
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    public static long getCursor(String type) {
        HashMap<String, Long> map = cursors();
        synchronized (LOCK) {
            Long v = map.get(String.valueOf(type));
            return v == null ? 0L : v.longValue();
        }
    }

    public static void setCursor(String type, long value) {
        HashMap<String, Long> map = cursors();
        synchronized (LOCK) {
            Long old = map.get(String.valueOf(type));
            if (old != null && old.longValue() >= value) {
                return;
            }
            map.put(String.valueOf(type), Long.valueOf(value));
        }
        saveCursors();
    }

    private static void saveCursors() {
        File f = cursorFile();
        if (f == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        synchronized (LOCK) {
            for (Map.Entry<String, Long> en : sCursor.entrySet()) {
                sb.append(en.getKey()).append('\t').append(en.getValue().longValue()).append('\n');
            }
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f, false);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.flush();
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    public static int size() {
        HashMap<String, long[]> map = index();
        synchronized (LOCK) {
            return map.size();
        }
    }

    public static void clearAll() {
        synchronized (LOCK) {
            if (sIndex != null) {
                sIndex.clear();
            }
            if (sCursor != null) {
                sCursor.clear();
            }
            sIndexBytes = 0L;
        }
        File a = indexFile();
        if (a != null && a.exists()) {
            a.delete();
        }
        File b = cursorFile();
        if (b != null && b.exists()) {
            b.delete();
        }
    }
}
