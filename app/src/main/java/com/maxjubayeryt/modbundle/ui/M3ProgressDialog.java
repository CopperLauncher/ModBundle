package com.maxjubayeryt.modbundle.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.maxjubayeryt.modbundle.utils.ThemeColors;

/**
 * Small drop-in replacement for the old android.app.ProgressDialog usages across the app.
 * ProgressDialog is deprecated and doesn't pick up the app's M3 theme (it renders with the
 * platform's default dialog chrome), which is why progress popups looked out of place next
 * to the rest of the now-M3'd UI. This wraps a MaterialAlertDialogBuilder with either an
 * indeterminate CircularProgressIndicator (the common case — "resolving", "checking", etc.)
 * or, when setProgressStyle(STYLE_HORIZONTAL) is called, a determinate LinearProgressIndicator
 * for download progress — matching the two ProgressDialog styles this replaced.
 */
public class M3ProgressDialog {
    public static final int STYLE_HORIZONTAL = 1;

    private final Context ctx;
    private final AlertDialog dialog;
    private final TextView titleView;
    private final TextView messageView;
    private final CircularProgressIndicator spinner;
    private LinearProgressIndicator bar;
    private final LinearLayout root;

    public M3ProgressDialog(Context ctx) {
        this.ctx = ctx;
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        titleView = new TextView(ctx);
        titleView.setTextColor(ThemeColors.onSurface(root));
        titleView.setTextSize(17);
        titleView.setVisibility(android.view.View.GONE);
        root.addView(titleView);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);
        root.addView(row, rowParams);

        spinner = new CircularProgressIndicator(ctx);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        spinnerParams.setMarginEnd(dp(20));
        row.addView(spinner, spinnerParams);

        messageView = new TextView(ctx);
        messageView.setTextColor(ThemeColors.onSurfaceVariant(row));
        messageView.setTextSize(15);
        messageView.setMaxLines(2);
        row.addView(messageView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        dialog = new MaterialAlertDialogBuilder(ctx)
                .setView(root)
                .setCancelable(true)
                .create();
    }

    /** Switches to a determinate horizontal bar instead of the default indeterminate spinner. */
    public void setProgressStyle(int style) {
        if (style != STYLE_HORIZONTAL || bar != null) return;
        ((LinearLayout) spinner.getParent()).removeView(spinner);
        bar = new LinearProgressIndicator(ctx);
        bar.setIndeterminate(false);
        bar.setMax(100);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = dp(8);
        root.addView(bar, barParams);
    }

    public void setMax(int max) { if (bar != null) bar.setMax(max); }
    public void setProgress(int progress) { if (bar != null) bar.setProgress(progress); }

    public void setTitle(String title) {
        titleView.setText(title);
        titleView.setVisibility(android.view.View.VISIBLE);
    }
    public void setMessage(String message) { messageView.setText(message); }
    public void setCancelable(boolean cancelable) { dialog.setCancelable(cancelable); }
    public void show() { dialog.show(); }
    public void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }

    private int dp(int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }
}

