package com.maxjubayeryt.modbundle;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.maxjubayeryt.modbundle.utils.PrefManager;

public class ModBundleApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyThemeMode(new PrefManager(this).getThemeMode());
        // Material You dynamic color: on Android 12+ this replaces the fixed M3 palette in
        // themes.xml with one derived from the device's wallpaper, applied automatically to
        // every Activity. On older devices this is a no-op and the fixed palette is used.
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    /** 0 = System Default, 1 = Light, 2 = Dark */
    public static void applyThemeMode(int mode) {
        int nightMode = mode == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
