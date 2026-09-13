package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

public class PrefManager {
    private static final String PREFS = "cuinstaller_prefs";
    private static final String KEY_INSTANCE_URI = "instance_folder_uri";
    private static final String KEY_GAME_VER     = "game_version";
    private static final String KEY_LOADER       = "mod_loader";

    private final SharedPreferences prefs;

    public PrefManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Save the instance root folder URI (e.g. .minecraft or instance root) */
    public void saveInstanceUri(Uri uri) {
        if (uri == null) {
            prefs.edit().remove(KEY_INSTANCE_URI).apply();
            return;
        }
        prefs.edit().putString(KEY_INSTANCE_URI, uri.toString()).apply();
    }

    public Uri getInstanceUri() {
        String s = prefs.getString(KEY_INSTANCE_URI, null);
        // migrate old key
        if (s == null) s = prefs.getString("mods_folder_uri", null);
        return s != null ? Uri.parse(s) : null;
    }

    @Deprecated
    public void saveFilters(String gameVersion, String loader) {
        prefs.edit().putString(KEY_GAME_VER, gameVersion).putString(KEY_LOADER, loader).apply();
    }

    public String getGameVersion() { return prefs.getString(KEY_GAME_VER, ""); }
    public String getLoader()      { return prefs.getString(KEY_LOADER, ""); }
    public boolean hasInstanceFolder() { return getInstanceUri() != null; }
    @Deprecated
    public boolean hasModsFolder() { return hasInstanceFolder(); }

    private static final String KEY_THEME_MODE = "theme_mode";
    /** 0 = System Default, 1 = Light, 2 = Dark */
    public void saveThemeMode(int mode) { prefs.edit().putInt(KEY_THEME_MODE, mode).apply(); }
    public int getThemeMode() { return prefs.getInt(KEY_THEME_MODE, 2); } // default: Dark, matching the app's prior forced-dark behavior

    private static final String KEY_COLOR_PRESET = "color_preset";
    private static final String KEY_USE_DYNAMIC_COLOR = "use_dynamic_color";
    /** 0 = Purple (default), 1 = Blue, 2 = Green, 3 = Orange, 4 = Pink */
    public void saveColorPreset(int preset) { prefs.edit().putInt(KEY_COLOR_PRESET, preset).apply(); }
    public int getColorPreset() { return prefs.getInt(KEY_COLOR_PRESET, 0); }
    /** Whether to follow the device wallpaper's colors (Android 12+ only) instead of the manual preset above. */
    public void saveUseDynamicColor(boolean use) { prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLOR, use).apply(); }
    public boolean getUseDynamicColor() { return prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true); }
}
