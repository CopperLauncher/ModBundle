package com.maxjubayeryt.modbundle;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Shown by ModBundleApp's uncaught-exception handler instead of letting the app just die.
 * Deliberately built with plain framework widgets and no custom theme/style reference — if
 * a theming bug is what caused the crash in the first place, this screen still needs to
 * render reliably, so it can't depend on anything that might be part of the problem.
 */
public class CrashActivity extends Activity {

    public static final String EXTRA_STACK_TRACE = "stack_trace";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String trace = getIntent().getStringExtra(EXTRA_STACK_TRACE);
        if (trace == null) trace = "(no stack trace available)";
        final String stackTrace = trace;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#151218"));
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ModBundle crashed");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Copy the details below and send them over so this can get fixed.");
        subtitle.setTextColor(Color.parseColor("#CBC4CF"));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(16);
        root.addView(subtitle, subtitleParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#211E24"));
        TextView traceView = new TextView(this);
        traceView.setText(stackTrace);
        traceView.setTextColor(Color.parseColor("#E7E0E8"));
        traceView.setTextSize(12);
        traceView.setTypeface(Typeface.MONOSPACE);
        traceView.setPadding(dp(12), dp(12), dp(12), dp(12));
        traceView.setTextIsSelectable(true);
        scroll.addView(traceView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonRowParams.topMargin = dp(16);
        root.addView(buttonRow, buttonRowParams);

        Button closeButton = new Button(this);
        closeButton.setText("Close App");
        closeButton.setOnClickListener(v -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });
        buttonRow.addView(closeButton);

        Button copyButton = new Button(this);
        copyButton.setText("Copy Details");
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ModBundle crash log", stackTrace));
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.setMarginStart(dp(12));
        buttonRow.addView(copyButton, copyParams);

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
