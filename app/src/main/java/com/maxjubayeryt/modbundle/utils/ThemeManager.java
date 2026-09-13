package com.maxjubayeryt.modbundle.utils;

import android.app.Activity;
import com.google.android.material.color.DynamicColors;
import com.maxjubayeryt.modbundle.R;

/**
 * Applies the app's color scheme to an Activity before setContentView(): either Material
 * You dynamic color (device wallpaper-derived, Android 12+ only) or one of a handful of
 * manual color presets that work on any Android version. Dynamic color on its own can't be
 * "switched" the way a person might expect from Settings — the OS decides it — so this is
 * what actually lets someone change the M3 color scheme on older devices, and lets a 12+
 * user opt out of wallpaper matching in favor of a fixed preset if they'd rather.
 */
public class ThemeManager {

    private static final int[] PRESET_OVERLAYS = {
            0, // Purple = the base AppTheme itself, no overlay needed
            R.style.ThemeOverlay_App_Blue,
            R.style.ThemeOverlay_App_Green,
            R.style.ThemeOverlay_App_Orange,
            R.style.ThemeOverlay_App_Pink,
    };

    /** Call at the very top of onCreate(), before setContentView(). */
    public static void apply(Activity activity) {
        // Defensive: a resource-resolution problem in here would otherwise take the whole
        // app down before a single screen renders, which is strictly worse than just
        // falling back to the default AppTheme colors for one launch.
        try {
            PrefManager prefs = new PrefManager(activity);
            boolean wantsDynamic = prefs.getUseDynamicColor();
            boolean dynamicAvailable = DynamicColors.isDynamicColorAvailable();

            if (wantsDynamic && dynamicAvailable) {
                DynamicColors.applyToActivityIfAvailable(activity);
                return;
            }

            int preset = prefs.getColorPreset();
            int overlay = (preset >= 0 && preset < PRESET_OVERLAYS.length) ? PRESET_OVERLAYS[preset] : 0;
            if (overlay != 0) {
                activity.getTheme().applyStyle(overlay, true);
            }
            // preset 0 (Purple) needs nothing extra — it's already what AppTheme defines.
        } catch (Exception e) {
            android.util.Log.e("ThemeManager", "Failed to apply color theme, falling back to default", e);
        }
    }
}
