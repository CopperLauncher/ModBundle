package com.modbundle.app.utils;

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
        if (uri == null) return;
        prefs.edit().putString(KEY_INSTANCE_URI, uri.toString()).apply();
    }

    public Uri getInstanceUri() {
        String s = prefs.getString(KEY_INSTANCE_URI, null);
        // migrate old key
        if (s == null) s = prefs.getString("mods_folder_uri", null);
        return s != null ? Uri.parse(s) : null;
    }

    /** @deprecated use getInstanceUri */
    public void saveFilters(String gameVersion, String loader) {
        prefs.edit().putString(KEY_GAME_VER, gameVersion).putString(KEY_LOADER, loader).apply();
    }

    public String getGameVersion() { return prefs.getString(KEY_GAME_VER, ""); }
    public String getLoader()      { return prefs.getString(KEY_LOADER, ""); }
    public boolean hasInstanceFolder() { return getInstanceUri() != null; }
    /** @deprecated use hasInstanceFolder */
    public boolean hasModsFolder() { return hasInstanceFolder(); }
}
