package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import androidx.documentfile.provider.DocumentFile;
import com.maxjubayeryt.modbundle.api.ModrinthApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves a real display name for installed content instead of just showing the raw
 * filename, in this order:
 *  1. The name embedded in the file itself — fabric.mod.json's "name", or Forge/NeoForge's
 *     "displayName" (see ModMetadataParser). Only mods carry this.
 *  2. The project's title on Modrinth/CurseForge, via whichever project id InstalledIndex
 *     already has on file for this filename — no re-hashing needed if it's already known
 *     (from a quick-install, or from ContentUpdateChecker's backfill).
 *  3. The raw filename, same as before — the fallback of last resort, not a first choice.
 *
 * Resolved names are cached in memory per filename for the session, since re-parsing a zip
 * or re-fetching a project title on every scroll/rebind would be wasteful.
 */
public class ContentNameResolver {

    public interface NameCallback { void onName(String displayName); }

    private static final Map<String, String> sCache = new ConcurrentHashMap<>();
    private static final ExecutorService sExecutor = Executors.newFixedThreadPool(2);
    private static final ModrinthApi sModrinth = new ModrinthApi();

    public static void resolve(Context ctx, Object file, String fileName, String instanceKey,
                               InstalledIndex index, NameCallback callback) {
        String cached = sCache.get(fileName);
        if (cached != null) { callback.onName(cached); return; }

        sExecutor.execute(() -> {
            String resolved = tryLocalMetadata(ctx, file);
            if (resolved != null) { deliver(fileName, resolved, callback); return; }

            String projectId = instanceKey != null
                    ? index.getInstalledProjectId(instanceKey, stripDisabled(fileName)) : null;
            if (projectId != null) {
                sModrinth.getProject(projectId, new ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
                    @Override public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult project) {
                        deliver(fileName, project != null && project.title != null ? project.title : fileName, callback);
                    }
                    @Override public void onError(String error) { deliver(fileName, fileName, callback); }
                });
                return;
            }
            deliver(fileName, fileName, callback);
        });
    }

    private static String tryLocalMetadata(Context ctx, Object file) {
        try {
            com.maxjubayeryt.modbundle.utils.ModMetadata meta = (file instanceof DocumentFile)
                    ? ModMetadataParser.parse(ctx, (DocumentFile) file)
                    : ModMetadataParser.parse((java.io.File) file);
            if (meta != null && meta.name != null && !meta.name.trim().isEmpty()) return meta.name.trim();
        } catch (Exception ignored) { }
        return null;
    }

    private static void deliver(String fileName, String name, NameCallback callback) {
        sCache.put(fileName, name);
        // Same pattern as ModIconLoader: this runs on the background executor thread, and
        // the callback touches a view (setText), so it has to be posted back to the main
        // thread — calling it directly here was the exact cause of the
        // CalledFromWrongThreadException crash.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onName(name));
    }

    private static String stripDisabled(String fileName) {
        return fileName != null && fileName.endsWith(".disabled")
                ? fileName.substring(0, fileName.length() - ".disabled".length()) : fileName;
    }

    /** Call after a rename (disable/enable/update) so the old filename's cached name doesn't linger under a stale key. */
    public static void invalidate(String fileName) {
        if (fileName != null) sCache.remove(fileName);
    }
}
