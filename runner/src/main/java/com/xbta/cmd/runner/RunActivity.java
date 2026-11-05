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
 *   "exec": "shell|root|shizuku|空",
 *   "showOutput": true,
 *   "timeoutSec": 30
 * }
 *
 * 行为约定：
 *  - type=shell：必须有 exec 身份，执行命令并显示输出
 *  - type=其它：exec 为空 → **直接调系统 Intent**，零 shell、零界面，点开即跳转；
 *               exec 非空 → 把同样的动作转成 am 命令，用该身份执行（给系统限制较严的场景兜底）
 *
 * Activity 在 manifest 里用半透明主题，所以成功路径上连一帧白屏都不会有。
 */
public class RunActivity extends Activity {

    private static final int SHIZUKU_REQ = 0x5A17;

    private TextView output;
    private JSONObject cfg;
    private boolean waitingShizuku = false;

    /** 当前类型（授权回调要按类型决定重跑哪条路径） */
    private String curType = "shell";

    /** 从 config 规范化出来的 Intent 要素 */
    private String fValue = "", fAction = "", fComponent = "", fData = "", fMime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = loadConfig();
        curType = cfg.optString("type", "shell");
        normalizeFields();

        if ("shell".equals(curType)) {
            buildUi(curType, cfg.optString("title", "Shell 命令"));
            doShell();
            return;
        }

        String exec = cfg.optString("exec", "").trim();
        if (exec.isEmpty()) {
            // 直接调系统接口：必须主线程
            String err = fireIntent(curType);
            if (err == null) {
                finish();
            } else {
                buildUi(curType, "执行失败");
                output.setText(err);
            }
            return;
        }
        // 走 shell 封装：子线程执行（可能等到超时），成功就直接退出
        final String useExec = exec;
        new Thread(() -> {
            String err = fireViaShell(curType, useExec);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (err == null) {
                    finish();
                } else {
                    ensureUi(err);
                }
            });
        }).start();
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

    /**
     * 整理 Intent 要素。容错：如果 value 其实是一整条 `am start -a … -d …` 命令
     * （老数据或手填），先把参数抠出来，绝不把它丢给 shell。
     */
    private void normalizeFields() {
        fValue = cfg.optString("value", "").trim();
        fAction = cfg.optString("action", "").trim();
        fComponent = cfg.optString("component", "").trim();
        fData = cfg.optString("data", "").trim();
        fMime = cfg.optString("mime", "").trim();

        if (looksLikeCommand(fValue)) {
            String[] p = parseAm(fValue);   // [action, data, component]
            if (p != null) {
                if (fAction.isEmpty()) fAction = p[0];
                if (fData.isEmpty()) fData = p[1];
                if (fComponent.isEmpty()) fComponent = p[2];
                fValue = "";
            }
        }
    }

    // ---------------- 路径一：直接调系统接口（默认，零 shell） ----------------

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
                    + "\n\n" + describe()
                    + "\n\n如果系统限制直接调用，可以在导出时勾一个执行身份改用 am 命令。";
        }
    }

    private Intent buildIntent(String type) {
        Intent i = new Intent();
        switch (type) {
            case "url": {
                String uri = fValue.isEmpty() ? fData : fValue;
                if (uri.isEmpty()) return null;
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse(uri));
                break;
            }
            case "broadcast": {
                String act = !fAction.isEmpty() ? fAction : fValue;
                if (act.isEmpty()) return null;
                i.setAction(act);
                if (!fData.isEmpty()) i.setData(Uri.parse(fData));
                break;
            }
            case "service": {
                if (fComponent.isEmpty() && fValue.isEmpty()) return null;
                if (!fAction.isEmpty()) i.setAction(fAction);
                if (!fData.isEmpty()) i.setData(Uri.parse(fData));
                break;
            }
            default: { // activity
                if (!fAction.isEmpty()) i.setAction(fAction);
                String uri = !fValue.isEmpty() ? fValue : fData;
                if (!uri.isEmpty()) i.setData(Uri.parse(uri));
                if (fAction.isEmpty() && uri.isEmpty() && fComponent.isEmpty()) return null;
            }
        }
        if (!fMime.isEmpty()) i.setType(fMime);
        String comp = fComponent.isEmpty() && type.equals("service") ? fValue : fComponent;
        if (!comp.isEmpty()) {
            String[] parts = splitComponent(comp);
            if (parts != null) i.setComponent(new ComponentName(parts[0], parts[1]));
        }
        putExtras(i);
        return i;
    }

    // ---------------- 路径二：转成 am 命令，用指定身份执行 ----------------

    /** 成功返回 null，失败返回输出（供显示） */
    private String fireViaShell(String type, String exec) {
        String am = buildAmCommand(type);
        if (am == null) return "配置不完整，缺少可用参数。\n\n" + describe();
        ShellResult r = runShell(am, exec);
        if (r.code == 0) return null;
        return "$ " + am + "\n\n" + r.text;
    }

    /** 把 config 里的 Intent 要素拼成 am 命令；参数不完整返回 null */
    private String buildAmCommand(String type) {
        String verb;
        String act = fAction;
        String uri = fData;
        String comp = fComponent;

        switch (type) {
            case "broadcast":
                verb = "am broadcast";
                if (act.isEmpty()) act = fValue;
                if (act.isEmpty()) return null;
                break;
            case "service":
                verb = "am startservice";
                if (comp.isEmpty()) comp = fValue;
                if (comp.isEmpty()) return null;
                break;
            case "url":
                verb = "am start";
                if (fValue.isEmpty() && uri.isEmpty()) return null;
                act = "android.intent.action.VIEW";
                uri = fValue.isEmpty() ? uri : fValue;
                comp = "";
                break;
            default:
                verb = "am start";
                if (!fValue.isEmpty()) uri = fValue;
                if (act.isEmpty() && uri.isEmpty() && comp.isEmpty()) return null;
        }

        StringBuilder sb = new StringBuilder(verb);
        if (!act.isEmpty()) sb.append(" -a ").append(q(act));
        if (!uri.isEmpty()) sb.append(" -d ").append(q(uri));
        if (!comp.isEmpty()) sb.append(" -n ").append(q(comp));
        if (!fMime.isEmpty()) sb.append(" -t ").append(q(fMime));
        String extras = cfg.optString("extras", "");
        if (!extras.isEmpty()) {
            for (String line : extras.split("\n")) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                int eq = s.indexOf('=');
                if (eq <= 0) continue;
                sb.append(" --es ").append(s.substring(0, eq).trim())
                        .append(" ").append(q(s.substring(eq + 1).trim()));
            }
        }
        return sb.toString();
    }

    /** 参数里只有这些字符时可以不加引号，其余一律单引号包起来 */
    private static String q(String s) {
        if (s.matches("[A-Za-z0-9_\\-.:/@=%,+~]+")) return s;
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private String describe() {
        StringBuilder sb = new StringBuilder("类型：").append(cfg.optString("type", ""));
        for (String k : new String[]{"value", "action", "component", "data"}) {
            String v = cfg.optString(k, "");
            if (!v.isEmpty()) sb.append("\n").append(k).append("：").append(v);
        }
        return sb.toString();
    }

    // ---------------- shell 执行（三种身份共用） ----------------

    private static class ShellResult {
        final int code;        // -1 表示没跑起来/被拦下
        final String text;

        ShellResult(int code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    private void doShell() {
        final String cmd = cfg.optString("value", "").trim();
        if (cmd.isEmpty()) {
            output.setText("配置里没有命令，可能是导出时没有写入。");
            return;
        }
        final String exec = cfg.optString("exec", "shell").trim().isEmpty()
                ? "shell" : cfg.optString("exec", "shell").trim();
        new Thread(() -> {
            ShellResult r = runShell(cmd, exec);
            StringBuilder sb = new StringBuilder("$ ").append(cmd).append("\n\n");
            if (!r.text.isEmpty()) sb.append(r.text);
            if (r.code >= 0) sb.append("\n[退出码 ").append(r.code).append("]");
            post(sb.toString());
        }).start();
    }

    private ShellResult runShell(String cmd, String exec) {
        try {
            Process p;
            if ("root".equals(exec)) {
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            } else if ("shizuku".equals(exec)) {
                if (!shizukuReady()) {
                    new Handler(Looper.getMainLooper()).post(this::requestShizuku);
                    return new ShellResult(-1, shizukuHint());
                }
                p = Shizuku.newProcess(new String[]{"sh", "-c", cmd}, null, null);
                if (p == null) return new ShellResult(-1, "Shizuku 创建进程失败。");
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
                return new ShellResult(-1, (out + "\n" + errBuf).trim() + "\n[超时 " + timeout + " 秒，已终止]");
            }
            String text = (out + (errBuf.length() > 0 ? "\n" + errBuf : "")).trim();
            return new ShellResult(proc.exitValue(), text);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder("执行失败：")
                    .append(e.getClass().getSimpleName()).append(" ")
                    .append(String.valueOf(e.getMessage()));
            if ("root".equals(exec)) sb.append("\n\n这台设备可能没有 root，或没有授权本应用。");
            if ("shizuku".equals(exec)) sb.append("\n\n").append(shizukuHint());
            return new ShellResult(-1, sb.toString());
        }
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
                ensureUi("Shizuku 版本过低，请升级到 11 以上。");
                return;
            }
            Shizuku.requestPermission(SHIZUKU_REQ);
        } catch (Throwable t) {
            ensureUi("请求 Shizuku 授权失败：" + t.getClass().getSimpleName());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != SHIZUKU_REQ) return;
        waitingShizuku = false;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            ensureUi("已拒绝 Shizuku 授权，无法用 ADB 权限执行。\n可以在系统设置里重新授权，或改用「普通 shell」身份导出。");
            return;
        }
        // 授权拿到后按原类型重跑一次
        if ("shell".equals(curType)) {
            doShell();
        } else {
            String err = fireViaShell(curType, cfg.optString("exec", "shizuku"));
            if (err == null) {
                finish();
            } else {
                ensureUi(err);
            }
        }
    }

    /** output 可能还没建（非 shell 的静默路径），这里按需补上 */
    private void ensureUi(String text) {
        if (output == null) buildUi(curType, "执行失败");
        output.setText(text);
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
