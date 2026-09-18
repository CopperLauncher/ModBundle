package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.google.gson.JsonObject;
import com.maxjubayeryt.modbundle.api.CurseForgeApi;
import com.maxjubayeryt.modbundle.api.ModrinthApi;
import com.maxjubayeryt.modbundle.model.ModVersion;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Content-type-agnostic update and icon resolution, ported from
 * CopperLauncher/Copper-Android's InstalledModAdapter (checkUpdateForEntry /
 * resolveRemoteIconUrl). Copper's version only ever needs to identify jars by
 * hash because most Forge/Fabric mods don't carry a clean self-reported id;
 * that hash-first approach turns out to be exactly what ModBundle needs for
 * resource packs and shader packs too, since those never carry an embedded
 * project id the way {@link ModMetadataParser} expects for mods.
 *
 * Resolution order for both updates and icons:
 *  1. SHA1 the installed file, ask Modrinth's version_file endpoint which
 *     project/version it belongs to (works for mods, resourcepacks, shaders).
 *  2. If Modrinth doesn't recognise it, murmur2-fingerprint it (Murmur2) and
 *     ask CurseForge's /fingerprints endpoint (also content-type agnostic).
 *  3. Whichever source resolves the file wins; if neither does, it's left
 *     alone (no update badge, icon falls back to a locally-extracted or
 *     default one).
 */
public class ContentUpdateChecker {

    public static class Result {
        public boolean hasUpdate;
        public String latestVersionName;
        public String latestFileUrl;
        public String latestFileName;
        public String iconUrl; // best-effort project icon, from whichever source matched
        // Always populated when the file was identified, even if it's already up to date —
        // used by InstalledIndex to recognise content that was installed before that index
        // existed, or installed by something other than this app.
        public String projectId;
        public String currentVersionId;
        public String source; // "modrinth" or "curseforge"
    }

    public interface ResultCallback {
        void onResult(Result result); // result == null if the file wasn't recognised anywhere
    }

    private final ModrinthApi modrinth = new ModrinthApi();
    private final CurseForgeApi curseforge = new CurseForgeApi();

    /** Checks a SAF-backed installed file (post-Android 10 instance storage). */
    public void check(Context context, DocumentFile file, String gameVersion, String loader, ResultCallback callback) {
        byte[] bytes = readAllBytes(context, file);
        if (bytes == null) { callback.onResult(null); return; }
        resolve(bytes, gameVersion, loader, callback);
    }

    /** Checks a plain java.io.File-backed installed file (legacy storage). */
    public void check(File file, String gameVersion, String loader, ResultCallback callback) {
        byte[] bytes = readAllBytes(file);
        if (bytes == null) { callback.onResult(null); return; }
        resolve(bytes, gameVersion, loader, callback);
    }

    private void resolve(byte[] bytes, String gameVersion, String loader, ResultCallback callback) {
        String sha1 = sha1Hex(bytes);
        if (sha1 == null) { callback.onResult(null); return; }

        modrinth.getVersionFromHash(sha1, currentVersion -> {
            String projectId = currentVersion != null ? extractProjectId(currentVersion) : null;
            if (projectId == null) {
                fallbackToCurseForge(bytes, callback);
                return;
            }
            modrinth.getVersions(projectId, nullToEmpty(gameVersion), nullToEmpty(loader), versions -> {
                if (versions == null || versions.isEmpty()) { callback.onResult(null); return; }
                ModVersion latest = versions.get(0);
                Result result = new Result();
                result.projectId = projectId;
                result.currentVersionId = currentVersion.id;
                result.source = "modrinth";
                // Same file already installed — nothing to report, regardless of on-disk name.
                if (currentVersion.id != null && currentVersion.id.equals(latest.id)) {
                    callback.onResult(result); // hasUpdate stays false, identity is still set
                    return;
                }
                ModVersion.VersionFile primary = ModDownloader.getPrimaryFile(latest);
                if (primary == null) { callback.onResult(result); return; }
                result.hasUpdate = true;
                result.latestVersionName = latest.versionNumber;
                result.latestFileUrl = primary.url;
                result.latestFileName = primary.filename;
                fetchModrinthIcon(projectId, result, callback);
            }, e -> callback.onResult(null));
        }, e -> fallbackToCurseForge(bytes, callback));
    }

    private void fetchModrinthIcon(String projectId, Result result, ResultCallback callback) {
        modrinth.getProject(projectId, new ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
            @Override public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult project) {
                if (project != null) result.iconUrl = project.iconUrl;
                callback.onResult(result);
            }
            @Override public void onError(String error) { callback.onResult(result); }
        });
    }

    private void fallbackToCurseForge(byte[] bytes, ResultCallback callback) {
        if (!CurseForgeApi.isEnabled()) { callback.onResult(null); return; }
        long fingerprint = Murmur2.hashBytes(bytes);
        curseforge.getFingerprintMatch(fingerprint, match -> {
            JsonObject file = match.has("file") ? match.getAsJsonObject("file") : null;
            if (file == null || !file.has("modId")) { callback.onResult(null); return; }
            int modId = file.get("modId").getAsInt();
            curseforge.getMod(modId, mod -> {
                Result result = new Result();
                result.projectId = String.valueOf(modId);
                result.source = "curseforge";
                if (mod.has("logo") && !mod.get("logo").isJsonNull()) {
                    JsonObject logo = mod.getAsJsonObject("logo");
                    if (logo.has("thumbnailUrl") && !logo.get("thumbnailUrl").isJsonNull()) {
                        result.iconUrl = logo.get("thumbnailUrl").getAsString();
                    }
                }
                // CurseForge's fingerprint match already tells us the exact file that's
                // installed; without another network round trip we can surface the icon
                // immediately and leave update detection to the next Modrinth-first pass
                // once/if the project is ever mirrored there. This mirrors Copper's own
                // behaviour, which only uses the CurseForge path for icon resolution.
                callback.onResult(result);
            }, e -> callback.onResult(null));
        }, e -> callback.onResult(null));
    }

    private static String extractProjectId(ModVersion version) {
        return version.projectId;
    }

    private static String nullToEmpty(String s) { return s != null ? s : ""; }

    private static byte[] readAllBytes(Context context, DocumentFile file) {
        try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) {
            if (is == null) return null;
            return readAllBytes(is);
        } catch (Exception e) { return null; }
    }

    private static byte[] readAllBytes(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return readAllBytes(is);
        } catch (Exception e) { return null; }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
