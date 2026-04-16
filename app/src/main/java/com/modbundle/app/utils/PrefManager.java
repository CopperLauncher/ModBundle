package com.modbundle.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PrefManager {
    private static final String PREFS = "cuinstaller_prefs";
    private static final String KEY_INSTANCE_URI = "instance_folder_uri";
    private static final String KEY_GAME_VER     = "game_version";
    private static final String KEY_LOADER       = "mod_loader";
    private static final String KEY_SAVED_PATHS  = "saved_instance_paths";
    private static final int MAX_SAVED = 10;

    private final SharedPreferences prefs;

    public PrefManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Save the instance root folder URI (e.g. .minecraft or instance root) */
    public void saveInstanceUri(Uri uri) {
        if (uri == null) return;
        String uriStr = uri.toString();
        prefs.edit().putString(KEY_INSTANCE_URI, uriStr).apply();
        Set<String> saved = new LinkedHashSet<>(getSavedPathStrings());
        saved.add(uriStr);
        List<String> list = new ArrayList<>(saved);
        while (list.size() > MAX_SAVED) list.remove(0);
        prefs.edit().putStringSet(KEY_SAVED_PATHS, new LinkedHashSet<>(list)).apply();
    }

    /** @deprecated use saveInstanceUri */
    public void saveModsUri(Uri uri) { saveInstanceUri(uri); }

    public Uri getInstanceUri() {
        String s = prefs.getString(KEY_INSTANCE_URI, null);
        // migrate old key
        if (s == null) s = prefs.getString("mods_folder_uri", null);
        return s != null ? Uri.parse(s) : null;
    }

    /** @deprecated use getInstanceUri */
    public Uri getModsUri() { return getInstanceUri(); }

    public List<Uri> getSavedPaths() {
        List<Uri> uris = new ArrayList<>();
        for (String s : getSavedPathStrings()) uris.add(Uri.parse(s));
        return uris;
    }

    public void removeSavedPath(Uri uri) {
        Set<String> saved = new LinkedHashSet<>(getSavedPathStrings());
        saved.remove(uri.toString());
        prefs.edit().putStringSet(KEY_SAVED_PATHS, saved).apply();
        if (uri.equals(getInstanceUri())) prefs.edit().remove(KEY_INSTANCE_URI).apply();
    }

    private Set<String> getSavedPathStrings() {
        return prefs.getStringSet(KEY_SAVED_PATHS, new LinkedHashSet<>());
    }

    public void saveFilters(String gameVersion, String loader) {
        prefs.edit().putString(KEY_GAME_VER, gameVersion).putString(KEY_LOADER, loader).apply();
    }

    public String getGameVersion() { return prefs.getString(KEY_GAME_VER, ""); }
    public String getLoader()      { return prefs.getString(KEY_LOADER, ""); }
    public boolean hasInstanceFolder() { return getInstanceUri() != null; }
    /** @deprecated use hasInstanceFolder */
    public boolean hasModsFolder() { return hasInstanceFolder(); }

    // Custom scan paths
    private static final String KEY_CUSTOM_SCAN_PATHS = "custom_scan_paths";

    public java.util.List<String> getCustomScanPaths() {
        java.util.Set<String> set = prefs.getStringSet(KEY_CUSTOM_SCAN_PATHS, new java.util.LinkedHashSet<>());
        return new java.util.ArrayList<>(set);
    }

    public void addCustomScanPath(String path) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>(getCustomScanPaths());
        set.add(path);
        prefs.edit().putStringSet(KEY_CUSTOM_SCAN_PATHS, set).apply();
    }

    public void removeCustomScanPath(String path) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>(getCustomScanPaths());
        set.remove(path);
        prefs.edit().putStringSet(KEY_CUSTOM_SCAN_PATHS, set).apply();
    }
}
