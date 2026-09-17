package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers which Modrinth/CurseForge project each installed file came from, so the browse
 * list can show Install / Installed / Update / Downgrade immediately — the way Copper's
 * search screen does — without a per-row network lookup.
 *
 * Copper resolves this by hashing the local jars (see ModsInstallApi / installedVersionIndex),
 * which is accurate but costs a hash + API round trip per file. Here that path still exists
 * for files the app didn't install itself (ContentUpdateChecker does exactly that hash
 * lookup during Check Updates, and calls {@link #record} with what it finds). This index is
 * the cheap fast path on top of it: anything installed through the app is recorded at
 * download time, so it's known instantly on every later browse.
 *
 * Entries are keyed per instance, since the same project can be installed in one instance
 * and not another.
 */
public class InstalledIndex {

    private static final String PREFS = "installed_index";

    private final SharedPreferences prefs;

    public InstalledIndex(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String instanceKey, String projectId) {
        return instanceKey + "|" + projectId;
    }

    /** Records that {@code projectId} is installed in this instance as {@code fileName} at {@code versionNumber}. */
    public void record(String instanceKey, String projectId, String fileName, String versionNumber) {
        if (instanceKey == null || projectId == null || fileName == null) return;
        prefs.edit()
                .putString(key(instanceKey, projectId), fileName + "\u0000" + (versionNumber != null ? versionNumber : ""))
                .apply();
    }

    public void forget(String instanceKey, String projectId) {
        if (instanceKey == null || projectId == null) return;
        prefs.edit().remove(key(instanceKey, projectId)).apply();
    }

    /** Removes whichever project maps to this filename — used when a file is deleted by name. */
    public void forgetByFileName(String instanceKey, String fileName) {
        if (instanceKey == null || fileName == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (String k : prefs.getAll().keySet()) {
            if (!k.startsWith(instanceKey + "|")) continue;
            String value = prefs.getString(k, null);
            if (value != null && fileName.equals(value.split("\u0000", -1)[0])) editor.remove(k);
        }
        editor.apply();
    }

    public boolean isInstalled(String instanceKey, String projectId) {
        return getInstalledFileName(instanceKey, projectId) != null;
    }

    public String getInstalledFileName(String instanceKey, String projectId) {
        if (instanceKey == null || projectId == null) return null;
        String value = prefs.getString(key(instanceKey, projectId), null);
        if (value == null) return null;
        String name = value.split("\u0000", -1)[0];
        return name.isEmpty() ? null : name;
    }

    public String getInstalledVersionNumber(String instanceKey, String projectId) {
        if (instanceKey == null || projectId == null) return null;
        String value = prefs.getString(key(instanceKey, projectId), null);
        if (value == null) return null;
        String[] parts = value.split("\u0000", -1);
        return parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
    }
}
