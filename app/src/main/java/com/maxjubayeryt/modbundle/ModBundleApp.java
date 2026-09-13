package com.maxjubayeryt.modbundle;

import android.app.Application;
import android.content.Intent;
import androidx.appcompat.app.AppCompatDelegate;
import com.maxjubayeryt.modbundle.utils.PrefManager;

public class ModBundleApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyThemeMode(new PrefManager(this).getThemeMode());
        // Color scheme (Material You dynamic color vs. a manual preset) is applied per
        // Activity by ThemeManager.apply(), called at the top of each Activity's onCreate
        // — not here app-wide — so a person can pick a fixed preset in Settings even on a
        // device where dynamic color would otherwise be available. See ThemeManager.

        installCrashHandler();
    }

    /**
     * Replaces the default "app just disappears" crash behavior with a screen showing the
     * full stack trace and a copy button, so a crash can actually be diagnosed instead of
     * just reported as "it crashed". The previous default handler is chained in afterward
     * in case something else (an OS-level crash reporter, etc.) also needs to see it.
     */
    private void installCrashHandler() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                String versionName;
                try {
                    versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception ignored) { versionName = "unknown"; }
                String trace = "ModBundle " + versionName
                        + " (" + android.os.Build.MODEL + ", Android " + android.os.Build.VERSION.RELEASE + ")\n\n" + sw;

                Intent intent = new Intent(this, CrashActivity.class);
                intent.putExtra(CrashActivity.EXTRA_STACK_TRACE, trace);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);

                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            } catch (Exception e) {
                if (previous != null) previous.uncaughtException(thread, throwable);
            }
        });
    }

    /** 0 = System Default, 1 = Light, 2 = Dark */
    public static void applyThemeMode(int mode) {
        int nightMode = mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
