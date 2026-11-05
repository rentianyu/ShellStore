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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/**
 * 导出 APK 的执行入口：读 assets/config.json → 执行 → 结束。
 *
 * config.json（导出时由主 App 写入）：
 * {
 *   "title": "显示名称",
 *   "type": "shell|url|activity|broadcast|service",
 *   "value": "命令 / URI / action / 组件名",
 *   "action": "可选 intent action",
 *   "component": "可选 package/class",
 *   "data": "可选 data URI",
 *   "extras": "可选，k=v 每行一个",
 *   "exec": "shell|root|shizuku",
 *   "showOutput": true,
 *   "timeoutSec": 30
 * }
 *
 * 设计原则：**非 shell 类型不做任何 shell 调用、不显示任何界面**。
 * 点图标 → 直接 startActivity / sendBroadcast / startService → 立即 finish()，
 * 和直接点目标 App 的图标没有区别。只有执行失败时才弹出来说明原因。
 *
 * Activity 在 manifest 里用半透明主题，所以成功路径上连一帧白屏都不会有。
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
        String type = cfg.optString("type", "shell");

        if ("shell".equals(type)) {
            // shell 才有界面：要看输出、要复制
            buildUi(type, cfg.optString("title", "Shell 命令"));
            doShell();
            return;
        }

        // 其余类型：直接触发，成功就退出，不闪界面
        String err = fireIntent(type);
        if (err == null) {
            finish();
        } else {
            buildUi(type, cfg.optString("title", "执行失败"));
            output.setText(err);
        }
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

    // ---------------- 非 shell：直接用 Intent，零 shell、零界面 ----------------

    /** 成功返回 null，失败返回可读原因 */
    private String fireIntent(String type) {
        try {
            Intent i = buildIntent(type);
            if (i == null) return "配置不完整，缺少可用参数。\n\n" + describe();
            switch (type) {
                case "broadcast":
                    sendBroadcast(i);
                    break;
                case "service":
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startService(i);
                    break;
                default:
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
            }
            return null;
        } catch (Exception e) {
            return "启动失败：" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : "\n" + e.getMessage())
                    + "\n\n" + describe();
        }
    }

    /**
     * 组装 Intent。这里做一层容错：如果 value 其实是一整条 `am start -a … -d …` 命令
     * （老数据或手填的），先把参数抠出来再走 Intent，绝不把它丢给 shell。
     */
    private Intent buildIntent(String type) {
        String value = cfg.optString("value", "").trim();
        String action = cfg.optString("action", "").trim();
        String component = cfg.optString("component", "").trim();
        String data = cfg.optString("data", "").trim();
        String mime = cfg.optString("mime", "").trim();

        if (looksLikeCommand(value)) {
            String[] p = parseAm(value);   // [action, data, component]
            if (p != null) {
                if (action.isEmpty()) action = p[0];
                if (data.isEmpty()) data = p[1];
                if (component.isEmpty()) component = p[2];
                value = "";
            }
        }

        Intent i = new Intent();
        switch (type) {
            case "url": {
                String uri = value.isEmpty() ? data : value;
                if (uri.isEmpty()) return null;
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse(uri));
                break;
            }
            case "broadcast": {
                String act = !action.isEmpty() ? action : value;
                if (act.isEmpty()) return null;
                i.setAction(act);
                if (!data.isEmpty()) i.setData(Uri.parse(data));
                break;
            }
            case "service": {
                if (component.isEmpty() && value.isEmpty()) return null;
                if (!action.isEmpty()) i.setAction(action);
                if (!data.isEmpty()) i.setData(Uri.parse(data));
                break;
            }
            default: { // activity
                if (!action.isEmpty()) i.setAction(action);
                String uri = !value.isEmpty() ? value : data;
                if (!uri.isEmpty()) i.setData(Uri.parse(uri));
                if (action.isEmpty() && uri.isEmpty() && component.isEmpty()) return null;
            }
        }
        if (!mime.isEmpty()) i.setType(mime);
        if (!component.isEmpty()) {
            String[] parts = splitComponent(component);
            if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
        } else if (type.equals("service") && !value.isEmpty()) {
            String[] parts = splitComponent(value);
            if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
        }
        putExtras(i);
        return i;
    }

    private String describe() {
        StringBuilder sb = new StringBuilder("类型：").append(cfg.optString("type", ""));
        for (String k : new String[]{"value", "action", "component", "data"}) {
            String v = cfg.optString(k, "");
            if (!v.isEmpty()) sb.append("\n").append(k).append("：").append(v);
        }
        return sb.toString();
    }

    // ---------------- shell：三种身份 ----------------

    private void doShell() {
        final String cmd = cfg.optString("value", "").trim();
        if (cmd.isEmpty()) {
            output.setText("配置里没有命令，可能是导出时没有写入。");
            return;
        }
        final String exec = cfg.optString("exec", "shell");
        new Thread(() -> post(exec(cmd, exec))).start();
    }

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
                sb.append(out).append(errBuf);
                sb.append("\n[超时 ").append(timeout).append(" 秒，已终止]");
            } else {
                if (!out.isEmpty()) sb.append(out);
                if (errBuf.length() > 0) sb.append(errBuf);
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

    // ---------------- 界面（只有 shell 与失败时才用） ----------------

    private void buildUi(String type, String title) {
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        // Activity 是半透明主题（为了非 shell 类型不闪），这里自己铺不透明底
        root.setBackgroundColor(Color.WHITE);

        TextView head = new TextView(this);
        head.setText(title);
        head.setTextColor(Color.parseColor("#1B1B1E"));
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        head.setPadding(0, 0, 0, (int) (10 * d));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        output = new TextView(this);
        output.setText("shell".equals(type) ? "执行中…" : "");
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
    }

    private void copyOutput() {
        CharSequence text = output.getText();
        if (text == null) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("output", text));
    }

    // ---------------- 命令容错解析 ----------------

    private static final Pattern CMD_HEAD = Pattern.compile("^(adb\\s+(shell\\s+)?|(sh|bash|su)\\s+-c\\s+)");

    private static boolean looksLikeCommand(String s) {
        String t = s.trim();
        if (t.isEmpty()) return false;
        return CMD_HEAD.matcher(t).find() || t.startsWith("am ");
    }

    /** 从 `am start -a X -d Y -n Z` 里抠出 [action, data, component]，抠不到返回 null */
    private static String[] parseAm(String raw) {
        String s = CMD_HEAD.matcher(raw.trim()).replaceFirst("").trim();
        if (s.length() >= 2 &&
                ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))) {
            s = s.substring(1, s.length() - 1);
        }
        String action = grab(s, "-a");
        String data = grab(s, "-d");
        String comp = grab(s, "-n");
        if (action.isEmpty() && data.isEmpty() && comp.isEmpty()) return null;
        return new String[]{action, data, comp};
    }

    private static String grab(String s, String flag) {
        Matcher m = Pattern.compile("(?:^|\\s)" + Pattern.quote(flag) + "\\s+('([^']*)'|\"([^\"]*)\"|(\\S+))").matcher(s);
        if (m.find()) {
            for (int i = 2; i <= 4; i++) {
                if (m.group(i) != null) return m.group(i);
            }
        }
        return "";
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
                if (output != null) output.setText("Shizuku 版本过低，请升级到 11 以上。");
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQ);
        } catch (Throwable t) {
            if (output != null) output.setText("请求 Shizuku 授权失败：" + t.getClass().getSimpleName());
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
        } else if (output != null) {
            output.setText("已拒绝 Shizuku 授权，无法用 ADB 权限执行。\n可以在系统设置里重新授权，或改用「普通 shell」身份导出。");
        }
    }

    // ---------------- 通用 ----------------

    private void post(String text) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (output != null) output.setText(text);
        });
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
