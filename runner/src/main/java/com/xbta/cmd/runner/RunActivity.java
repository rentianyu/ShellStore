package com.xbta.cmd.runner;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 导出 APK 的界面：读 assets 里的命令 → 执行 → 显示输出。
 *
 * 刻意不用任何第三方库、也不写 layout xml（界面用代码搭），
 * 目的只有一个：让导出给用户的 APK 尽量小、依赖尽量少。
 * 命令内容与标题都由主 App 在导出时写进 assets，这里不做任何业务判断。
 */
public class RunActivity extends Activity {

    private TextView output;
    private TextView head;
    private Button copyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String title = readAsset("title.txt", "Shell 命令");
        String cmd = readAsset("cmd.txt", "");

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        head = new TextView(this);
        head.setText(title);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        head.setPadding(0, 0, 0, (int) (10 * d));
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        output = new TextView(this);
        output.setText("执行中…");
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

        copyBtn = new Button(this);
        copyBtn.setText("复制输出");
        copyBtn.setOnClickListener(v -> copyOutput());
        bar.addView(copyBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> finish());
        bar.addView(close, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        if (cmd.trim().isEmpty()) {
            output.setText("模板里没有命令，可能是导出时没有写入。");
            return;
        }
        execute(cmd);
    }

    private void copyOutput() {
        CharSequence text = output.getText();
        if (text == null) return;
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(
                android.content.ClipData.newPlainText("output", text));
    }

    private void execute(final String cmd) {
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("$ ").append(cmd).append("\n\n");
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                String out = readAll(p.getInputStream());
                String err = readAll(p.getErrorStream());
                int code = p.waitFor();
                if (!out.isEmpty()) sb.append(out);
                if (!err.isEmpty()) sb.append(err);
                sb.append("\n[退出码 ").append(code).append("]");
            } catch (IOException | InterruptedException e) {
                sb.append("执行异常：").append(e.getClass().getSimpleName())
                        .append(" ").append(String.valueOf(e.getMessage()));
            }
            final String text = sb.toString();
            new Handler(Looper.getMainLooper()).post(() -> output.setText(text));
        }).start();
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
