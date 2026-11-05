package com.xbta.cmd.runner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * 导出 APK 的执行界面：读 assets/config.json → 按类型执行 → 显示结果。
 *
 * config.json 结构（导出时由主 App 写入）：
 * {
 *   "title": "显示名称",
 *   "type": "shell|url|activity|broadcast|service",
 *   "value": "命令 / URL / action",
 *   "action": "可选 intent action",
 *   "component": "可选 package/class",
 *   "extras": "可选，k=v 每行一个",
 *   "exec": "shell|root|shizuku",
 *   "showOutput": true,
 *   "timeoutSec": 30
 * }
 *
 * 刻意不用任何第三方库（Shizuku 除外，它是「借 ADB 权限」的必需依赖），
 * 界面全部用代码搭，目的只有一个：让导出的 APK 尽量小。
 */
public class RunActivity extends Activity {

    private static final int SHIZUKU_REQ = 0x5A17;

    private TextView output;
    private JSONObject cfg;
    private boolean waitingShizuku = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cfg = loadConfig();
        String title = cfg.optString("title", "Shell 命令");
        String type = cfg.optString("type", "shell");

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        head.setPadding(0, 0, 0, (int) (10 * d));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        output = new TextView(this);
        output.setText(type.equals("shell") ? "执行中…" : "准备中…");
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        output.setTextColor(Color.parseColor("#E4E2E4"));
        output.setBackgroundColor(Color.parseColor("#131315"));
        output.setPadding(pad, pad, pad, pad);
        output.setMovementMethod(new ScrollingMovementMethod());
        output.setTextIsSelectable(true);
        root.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, (int) (10 * d), 0, 0);

        Button copy = new Button(this);
        copy.setText("复制输出");
        copy.setOnClickListener(v -> copyOutput());
        bar.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        run();
    }

    /** 逐个字段读，缺字段就用默认值；读不到配置文件时退回旧的 cmd.txt / title.txt */
    private JSONObject loadConfig() {
        JSONObject o = new JSONObject();
        try {
            String raw = readAsset("config.json", "");
            if (!raw.isEmpty()) o = new JSONObject(raw);
        } catch (Exception ignored) {
        }
        if (!o.has("value")) {
            String cmd = readAsset("cmd.txt", "");
            if (!cmd.isEmpty()) {
                try {
                    o.put("type", "shell");
                    o.put("value", cmd);
                    o.put("exec", "shell");
                } catch (Exception ignored) {
                }
            }
        }
        if (!o.has("title")) {
            try {
                o.put("title", readAsset("title.txt", "Shell 命令"));
            } catch (Exception ignored) {
            }
        }
        return o;
    }

    // ---------------- 按类型执行 ----------------

    private void run() {
        String type = cfg.optString("type", "shell");
        switch (type) {
            case "url":
                doUrl();
                break;
            case "activity":
                doActivity();
                break;
            case "broadcast":
                doBroadcast();
                break;
            case "service":
                doService();
                break;
            default:
                doShell();
        }
    }

    private void doShell() {
        final String cmd = cfg.optString("value", "").trim();
        if (cmd.isEmpty()) {
            output.setText("配置里没有命令，可能是导出时没有写入。");
            return;
        }
        final String exec = cfg.optString("exec", "shell");
        new Thread(() -> {
            String text = exec(cmd, exec);
            post(text);
        }).start();
    }

    /** 三种身份：本应用 shell / su / Shizuku */
    private String exec(String cmd, String exec) {
        StringBuilder sb = new StringBuilder();
        sb.append("$ ").append(cmd).append("\n\n");
        try {
            Process p;
            if ("root".equals(exec)) {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            } else if ("shizuku".equals(exec)) {
                if (!shizukuReady()) {
                    new Handler(Looper.getMainLooper()).post(this::requestShizuku);
                    return sb.append(shizukuHint()).toString();
                }
                p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
                if (p == null) return sb.append("Shizuku 创建进程失败。").toString();
            } else {
                p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            }
            final Process proc = p;
            // stderr 是独立流，必须两个线程分别读，否则缓冲写满会卡死
            StringBuilder errBuf = new StringBuilder();
            Thread errReader = new Thread(() -> {
                try {
                    errBuf.append(readAll(proc.getErrorStream()));
                } catch (IOException ignored) {
                }
            });
            errReader.start();
            String out = readAll(proc.getInputStream());
            errReader.join(5000);

            int timeout = cfg.optInt("timeoutSec", 30);
            boolean done = proc.waitFor(Math.max(1, timeout), TimeUnit.SECONDS);
            if (!done) {
                proc.destroy();
                sb.append(out);
                sb.append(errBuf);
                sb.append("\n[超时 ").append(timeout).append(" 秒，已终止]");
            } else {
                if (!out.isEmpty()) sb.append(out);
                if (!errBuf.toString().isEmpty()) sb.append(errBuf);
                sb.append("\n[退出码 ").append(proc.exitValue()).append("]");
            }
        } catch (Exception e) {
            sb.append("执行失败：").append(e.getClass().getSimpleName())
                    .append(" ").append(String.valueOf(e.getMessage()));
            if ("root".equals(exec)) sb.append("\n\n这台设备可能没有 root，或没有授权本应用。");
            if ("shizuku".equals(exec)) sb.append("\n\n").append(shizukuHint());
        }
        return sb.toString();
    }

    private void doUrl() {
        String url = cfg.optString("value", "").trim();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            post("已打开：\n" + url);
        } catch (Exception e) {
            post("无法打开：" + e.getClass().getSimpleName() + "\n" + e.getMessage() + "\n\n" + url);
        }
    }

    private void doActivity() {
        String value = cfg.optString("value", "").trim();
        String action = cfg.optString("action", "").trim();
        String comp = cfg.optString("component", "").trim();
        try {
            Intent i = new Intent();
            if (!action.isEmpty()) i.setAction(action);
            if (!value.isEmpty()) i.setData(Uri.parse(value));
            else i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (!comp.isEmpty()) {
                String[] parts = splitComponent(comp);
                if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            putExtras(i);
            startActivity(i);
            post("已启动：\n" + (comp.isEmpty() ? (action.isEmpty() ? value : action) : comp));
        } catch (Exception e) {
            post("启动失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }

    private void doBroadcast() {
        String action = cfg.optString("value", "").trim();
        if (action.isEmpty()) action = cfg.optString("action", "").trim();
        try {
            Intent i = new Intent(action);
            String comp = cfg.optString("component", "").trim();
            if (!comp.isEmpty()) {
                String[] parts = splitComponent(comp);
                if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
            }
            putExtras(i);
            sendBroadcast(i);
            post("已发送广播：\n" + action);
        } catch (Exception e) {
            post("发送失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }

    private void doService() {
        String comp = cfg.optString("component", "").trim();
        try {
            Intent i = new Intent();
            String action = cfg.optString("action", "").trim();
            if (!action.isEmpty()) i.setAction(action);
            if (!comp.isEmpty()) {
                String[] parts = splitComponent(comp);
                if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
            }
            putExtras(i);
            startService(i);
            post("已启动服务：\n" + comp);
        } catch (Exception e) {
            post("启动失败：" + e.getClass().getSimpleName() + "\n" + e.getMessage());
        }
    }

    /** "pkg/.Cls" 或 "pkg/pkg.Cls" 都能解析 */
    private String[] splitComponent(String comp) {
        int i = comp.indexOf('/');
        if (i <= 0 || i >= comp.length() - 1) return null;
        String pkg = comp.substring(0, i);
        String cls = comp.substring(i + 1);
        if (cls.startsWith(".")) cls = pkg + cls;
        return new String[]{pkg, cls};
    }

    /** extras 写成「k=v」每行一个 */
    private void putExtras(Intent i) {
        String raw = cfg.optString("extras", "");
        if (raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            int eq = s.indexOf('=');
            if (eq <= 0) continue;
            i.putExtra(s.substring(0, eq).trim(), s.substring(eq + 1).trim());
        }
    }

    // ---------------- Shizuku ----------------

    private boolean shizukuReady() {
        try {
            if (!Shizuku.pingBinder()) return false;
            if (Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private String shizukuHint() {
        boolean installed;
        try {
            getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            installed = true;
        } catch (Exception e) {
            installed = false;
        }
        if (!installed) return "这台设备没装 Shizuku。装了 Shizuku 并启动服务后，才能用 ADB 权限执行。";
        boolean running;
        try {
            running = Shizuku.pingBinder();
        } catch (Throwable t) {
            running = false;
        }
        if (!running) return "Shizuku 服务没在运行，请先打开 Shizuku 启动服务。";
        return "还没有授权本应用使用 Shizuku，请在弹出的对话框里点「允许」。";
    }

    private void requestShizuku() {
        try {
            if (waitingShizuku) return;
            waitingShizuku = true;
            if (Shizuku.isPreV11()) {
                output.setText("Shizuku 版本过低，请升级到 11 以上。");
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQ);
        } catch (Throwable t) {
            output.setText("请求 Shizuku 授权失败：" + t.getClass().getSimpleName());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != SHIZUKU_REQ) return;
        waitingShizuku = false;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            doShell();
        } else {
            output.setText("已拒绝 Shizuku 授权，无法用 ADB 权限执行。\n可以在系统设置里重新授权，或改用「普通 shell」身份导出。");
        }
    }

    // ---------------- 通用 ----------------

    private void post(String text) {
        new Handler(Looper.getMainLooper()).post(() -> output.setText(text));
    }

    private void copyOutput() {
        CharSequence text = output.getText();
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("output", text));
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }

    private String readAsset(String name, String fallback) {
        try (InputStream in = getAssets().open(name)) {
            String s = readAll(in);
            return s.isEmpty() ? fallback : s;
        } catch (IOException e) {
            return fallback;
        }
    }
}
