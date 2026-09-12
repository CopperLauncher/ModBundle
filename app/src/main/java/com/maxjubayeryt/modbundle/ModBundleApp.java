package com.maxjubayeryt.modbundle;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public class ModBundleApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        // Material You dynamic color: on Android 12+ this replaces the fixed M3 palette in
        // themes.xml with one derived from the device's wallpaper, applied automatically to
        // every Activity. On older devices this is a no-op and the fixed palette is used.
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
