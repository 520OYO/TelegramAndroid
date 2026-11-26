package org.telegram.messenger.backup;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;

/**
 * 频道备份配置界面：
 * 1. 设置三个本地目录（照片 / 视频 / 音乐）、目标频道、日志频道、黑白名单、间隔 / 重试 / 批量上限；
 * 2. 点"开始上传"串行批量上传，显示实时进度，可随时取消；
 * 3. 点"清空索引"删除本地指纹表与游标，下次重新扫描。
 * <p>
 * 本仓库工作区不含 res 目录，所有界面均用代码动态构建，不依赖布局 XML。
 */
public class BackupConfigDialog {

    private interface ValueSink {
        void onValue(String v);
    }

    /* ======================== 主界面 ======================== */

    public static void show(final Context context) {
        final BackupConfig cfg = BackupConfig.getInstance();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));

        addRow(context, root, "照片目录", cfg.getDirPhoto(), new Runnable() {
            @Override
            public void run() {
                editText(context, "照片目录", cfg.getDirPhoto(), false, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setDirPhoto(v);
                        show(context);
                    }
                });
            }
        });
        addRow(context, root, "视频目录", cfg.getDirVideo(), new Runnable() {
            @Override
            public void run() {
                editText(context, "视频目录", cfg.getDirVideo(), false, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setDirVideo(v);
                        show(context);
                    }
                });
            }
        });
        addRow(context, root, "音乐目录", cfg.getDirMusic(), new Runnable() {
            @Override
            public void run() {
                editText(context, "音乐目录", cfg.getDirMusic(), false, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setDirMusic(v);
                        show(context);
                    }
                });
            }
        });

        addRow(context, root, "目标频道（素材发这里）",
                cfg.getTargetChannel() == 0 ? "未设置，点此填写 -100 开头的频道 ID" : String.valueOf(cfg.getTargetChannel()),
                new Runnable() {
                    @Override
                    public void run() {
                        editText(context, "目标频道 ID", cfg.getTargetChannel() == 0 ? "" : String.valueOf(cfg.getTargetChannel()), true, new ValueSink() {
                            @Override
                            public void onValue(String v) {
                                cfg.setTargetChannel(parseLong(v));
                                show(context);
                            }
                        });
                    }
                });

        addRow(context, root, "日志频道（只发文字日志，留空则不记录）",
                cfg.getLogChannel() == 0 ? "未设置（不发送日志）" : String.valueOf(cfg.getLogChannel()),
                new Runnable() {
                    @Override
                    public void run() {
                        editText(context, "日志频道 ID", cfg.getLogChannel() == 0 ? "" : String.valueOf(cfg.getLogChannel()), true, new ValueSink() {
                            @Override
                            public void onValue(String v) {
                                cfg.setLogChannel(parseLong(v));
                                show(context);
                            }
                        });
                    }
                });

        addRow(context, root, "频道白名单（逗号分隔的 ID，留空=全部允许）",
                TextUtils.isEmpty(cfg.getWhitelist()) ? "未设置" : cfg.getWhitelist(),
                new Runnable() {
                    @Override
                    public void run() {
                        editText(context, "白名单", cfg.getWhitelist(), false, new ValueSink() {
                            @Override
                            public void onValue(String v) {
                                cfg.setWhitelist(v);
                                show(context);
                            }
                        });
                    }
                });

        addRow(context, root, "频道黑名单（逗号分隔的 ID，日志频道请放这里）",
                TextUtils.isEmpty(cfg.getBlacklist()) ? "未设置" : cfg.getBlacklist(),
                new Runnable() {
                    @Override
                    public void run() {
                        editText(context, "黑名单", cfg.getBlacklist(), false, new ValueSink() {
                            @Override
                            public void onValue(String v) {
                                cfg.setBlacklist(v);
                                show(context);
                            }
                        });
                    }
                });

        addRow(context, root, "每条间隔（毫秒，最小 300）", String.valueOf(cfg.getIntervalMs()), new Runnable() {
            @Override
            public void run() {
                editText(context, "间隔毫秒", String.valueOf(cfg.getIntervalMs()), true, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setIntervalMs((int) parseLong(v));
                        show(context);
                    }
                });
            }
        });

        addRow(context, root, "失败重试次数（最小 1）", String.valueOf(cfg.getMaxRetry()), new Runnable() {
            @Override
            public void run() {
                editText(context, "重试次数", String.valueOf(cfg.getMaxRetry()), true, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setMaxRetry((int) parseLong(v));
                        show(context);
                    }
                });
            }
        });

        addRow(context, root, "单次批量上限（最小 1）", String.valueOf(cfg.getBatchLimit()), new Runnable() {
            @Override
            public void run() {
                editText(context, "批量上限", String.valueOf(cfg.getBatchLimit()), true, new ValueSink() {
                    @Override
                    public void onValue(String v) {
                        cfg.setBatchLimit((int) parseLong(v));
                        show(context);
                    }
                });
            }
        });

        addRow(context, root, "已记录指纹数", BackupStore.size() + " 条（点击可清空索引）", new Runnable() {
            @Override
            public void run() {
                confirmClear(context);
            }
        });

        ScrollView sv = new ScrollView(context);
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle("频道备份（本地文件夹 → 频道）")
                .setView(sv)
                .setPositiveButton("开始上传", (d, w) -> startUpload(context))
                .setNeutralButton("清空索引", (d, w) -> confirmClear(context))
                .setNegativeButton("关闭", null)
                .show();
    }

    /* ======================== 上传 ======================== */

    private static void startUpload(final Context context) {
        final BackupConfig cfg = BackupConfig.getInstance();
        if (cfg.getTargetChannel() == 0) {
            Toast.makeText(context, "请先设置目标频道 ID（-100 开头）", Toast.LENGTH_LONG).show();
            return;
        }
        if (BackupUploader.isRunning()) {
            Toast.makeText(context, "正在上传中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        final TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tv.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        tv.setText("准备中…");

        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("频道备份")
                .setView(tv)
                .setNegativeButton("取消上传", (d, w) -> BackupUploader.cancel())
                .create();
        progress.setCancelable(false);
        progress.show();

        boolean ok = BackupUploader.start(UserConfig.selectedAccount, true, true, true,
                new BackupUploader.Callback() {
                    @Override
                    public void onProgress(int finished, int total, String name) {
                        tv.setText("进度 " + finished + " / " + total + (TextUtils.isEmpty(name) ? "" : ("\n" + name)));
                    }

                    @Override
                    public void onFinish(int success, int failed, String message) {
                        if (progress.isShowing()) {
                            progress.dismiss();
                        }
                        String text = TextUtils.isEmpty(message)
                                ? ("成功 " + success + "，失败 " + failed)
                                : message;
                        new AlertDialog.Builder(context)
                                .setTitle("上传结束")
                                .setMessage(text)
                                .setPositiveButton("好", null)
                                .show();
                    }
                });

        if (!ok) {
            if (progress.isShowing()) {
                progress.dismiss();
            }
            Toast.makeText(context, "无法开始上传（可能正在运行或没有新文件）", Toast.LENGTH_LONG).show();
        }
    }

    private static void confirmClear(final Context context) {
        new AlertDialog.Builder(context)
                .setTitle("清空已传索引")
                .setMessage("将删除本地指纹表与游标，下次会重新扫描全部文件，已存在于频道里的文件可能被重复上传。确定清空吗？")
                .setPositiveButton("确定清空", (d, w) -> {
                    BackupStore.clearAll();
                    Toast.makeText(context, "索引已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /* ======================== 通用 ======================== */

    private static void addRow(final Context context, LinearLayout parent, String label, String value, final Runnable onClick) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(10), AndroidUtilities.dp(24), AndroidUtilities.dp(10));

        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        t.setText(label);
        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(context);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        v.setText(TextUtils.isEmpty(value) ? "未设置" : value);
        v.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        box.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        });
        parent.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void editText(final Context context, String title, String current, boolean numeric, final ValueSink sink) {
        final EditText et = new EditText(context);
        et.setSingleLine(true);
        et.setText(current == null ? "" : current);
        if (numeric) {
            et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }

        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        wrap.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String v = et.getText() == null ? "" : et.getText().toString().trim();
                    sink.onValue(v);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static long parseLong(String v) {
        if (TextUtils.isEmpty(v)) {
            return 0L;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (Throwable e) {
            return 0L;
        }
    }
}
