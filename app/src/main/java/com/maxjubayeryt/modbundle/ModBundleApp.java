package com.maxjubayeryt.modbundle;

import android.app.Application;
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
    }

    /** 0 = System Default, 1 = Light, 2 = Dark */
    public static void applyThemeMode(int mode) {
        int nightMode = mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
