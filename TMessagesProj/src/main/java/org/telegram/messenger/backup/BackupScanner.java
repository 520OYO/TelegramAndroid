package org.telegram.messenger.backup;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * E module scanner: walk the three local folders, drop already-uploaded files,
 * return a time-ordered pending list. Fingerprint table is authoritative;
 * the cursor is only kept for progress display.
 */
public class BackupScanner {

    public static final String TYPE_PHOTO = "p";
    public static final String TYPE_VIDEO = "v";
    public static final String TYPE_MUSIC = "m";

    private static final String[] PHOTO_EXT = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif", ".gif", ".bmp"};
    private static final String[] VIDEO_EXT = {".mp4", ".mkv", ".mov", ".3gp", ".webm", ".avi", ".m4v", ".ts"};
    private static final String[] MUSIC_EXT = {".mp3", ".m4a", ".ogg", ".opus", ".flac", ".wav", ".aac", ".amr"};

    public static class Item {
        public final String type;
        public final String path;
        public final long size;
        public final long mtime;

        public Item(String type, String path, long size, long mtime) {
            this.type = type;
            this.path = path;
            this.size = size;
            this.mtime = mtime;
        }

        public String typeName() {
            return typeName(type);
        }
    }

    public static String typeName(String type) {
        if (TYPE_PHOTO.equals(type)) {
            return "\u7167\u7247";
        }
        if (TYPE_VIDEO.equals(type)) {
            return "\u89c6\u9891";
        }
        if (TYPE_MUSIC.equals(type)) {
            return "\u97f3\u4e50";
        }
        return "\u6587\u4ef6";
    }

    public static ArrayList<Item> scan(boolean photos, boolean videos, boolean musics) {
        ArrayList<Item> out = new ArrayList<>();
        BackupConfig cfg = BackupConfig.getInstance();
        if (photos) {
            collect(TYPE_PHOTO, cfg.getPhotoDir(), PHOTO_EXT, out);
        }
        if (videos) {
            collect(TYPE_VIDEO, cfg.getVideoDir(), VIDEO_EXT, out);
        }
        if (musics) {
            collect(TYPE_MUSIC, cfg.getMusicDir(), MUSIC_EXT, out);
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.mtime < b.mtime) {
                    return -1;
                }
                if (a.mtime > b.mtime) {
                    return 1;
                }
                return a.path.compareTo(b.path);
            }
        });
        int limit = cfg.getBatchLimit();
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private static void collect(String type, String dirPath, String[] exts, ArrayList<Item> out) {
        if (dirPath == null || dirPath.length() == 0) {
            return;
        }
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable e) {
            FileLog.e(e);
            return;
        }
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f == null) {
                continue;
            }
            try {
                if (!f.isFile() || !f.canRead()) {
                    continue;
                }
                long size = f.length();
                if (size <= 0L) {
                    continue;
                }
                String name = f.getName();
                if (name.length() == 0 || name.startsWith(".")) {
                    continue;
                }
                if (!matchExt(name, exts)) {
                    continue;
                }
                String path = f.getAbsolutePath();
                long mtime = f.lastModified();
                if (BackupStore.isUploaded(type, path, size, mtime)) {
                    continue;
                }
                out.add(new Item(type, path, size, mtime));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private static boolean matchExt(String name, String[] exts) {
        String lower = name.toLowerCase();
        for (String ext : exts) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
