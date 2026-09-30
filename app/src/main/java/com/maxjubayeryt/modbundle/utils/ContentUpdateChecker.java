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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        // True once the icon lookup actually finished (project fetched / CurseForge matched),
        // as opposed to failing on a rate limit or network blip. A Result whose icon lookup
        // failed must NOT be cached, otherwise that transient failure sticks for the whole
        // session and the row keeps its placeholder forever — the shader-icon bug.
        public boolean iconResolved;
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

    // Multiple independent callers — the icon loader, the display-name resolver, and the
    // install-index backfill — all need the same per-file identity, and were each triggering
    // their own hash + network resolution for the same file at nearly the same time right
    // after the Installed tab loaded. That redundant work (up to 2-3x the network calls and
    // executor contention it needed) was the actual cause of the multi-second load with many
    // mods installed. Caching the resolved Result per file, and coalescing concurrent
    // requests for a file that's already being resolved, means each file is only ever
    // hashed and looked up once per session, however many things ask for it.
    private static final Map<String, Result> sResultCache = new ConcurrentHashMap<>();
    private static final Map<String, java.util.List<ResultCallback>> sInFlight = new ConcurrentHashMap<>();

    /**
     * Checks a SAF-backed installed file (post-Android 10 instance storage). The SHA1 used
     * for the primary Modrinth lookup is computed by streaming the file rather than
     * buffering it whole — shader packs in particular are routinely 50-100+MB, and reading
     * one entirely into a byte[] just to hash it was needless memory pressure on every icon
     * load. The full bytes are only read into memory as a fallback, and only when Modrinth
     * doesn't recognise the file, since CurseForge's fingerprint match needs the actual
     * byte content for its whitespace-stripped murmur2 hash.
     */
    public void check(Context context, DocumentFile file, String gameVersion, String loader, ResultCallback callback) {
        String tag = file.getUri().toString();
        if (deliverCachedOrQueue(tag, callback)) return;
        String sha1;
        try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) {
            if (is == null) { finishAndFlush(tag, null); return; }
            sha1 = sha1Hex(is);
        } catch (Throwable e) { finishAndFlush(tag, null); return; }
        if (sha1 == null) { finishAndFlush(tag, null); return; }
        resolve(sha1, () -> readAllBytes(context, file), gameVersion, loader, result -> finishAndFlush(tag, result));
    }

    /** Checks a plain java.io.File-backed installed file (legacy storage). Same streaming approach as above. */
    public void check(File file, String gameVersion, String loader, ResultCallback callback) {
        String tag = file.getAbsolutePath();
        if (deliverCachedOrQueue(tag, callback)) return;
        String sha1;
        try (InputStream is = new FileInputStream(file)) {
            sha1 = sha1Hex(is);
        } catch (Throwable e) { finishAndFlush(tag, null); return; }
        if (sha1 == null) { finishAndFlush(tag, null); return; }
        resolve(sha1, () -> readAllBytes(file), gameVersion, loader, result -> finishAndFlush(tag, result));
    }

    /**
     * Returns true (and either delivers a cached result or queues the callback for delivery
     * once the in-flight resolution finishes) if nothing further needs to be started for this
     * tag. Returns false if the caller needs to actually kick off resolution — and has, by
     * that point, registered as the one doing so, so a second concurrent caller for the same
     * tag always sees itself as queued rather than also starting a resolution.
     */
    private boolean deliverCachedOrQueue(String tag, ResultCallback callback) {
        Result cached = sResultCache.get(tag);
        if (cached != null) { callback.onResult(cached); return true; }
        synchronized (sInFlight) {
            java.util.List<ResultCallback> waiters = sInFlight.get(tag);
            if (waiters != null) { waiters.add(callback); return true; }
            java.util.List<ResultCallback> mine = new java.util.ArrayList<>();
            mine.add(callback);
            sInFlight.put(tag, mine);
            return false;
        }
    }

    private void finishAndFlush(String tag, Result result) {
        // Null results, and results whose icon lookup failed transiently, aren't cached —
        // worth retrying later (e.g. rate limit or network blip).
        if (result != null && (result.iconResolved || result.iconUrl != null)) sResultCache.put(tag, result);
        java.util.List<ResultCallback> waiters;
        synchronized (sInFlight) { waiters = sInFlight.remove(tag); }
        if (waiters != null) for (ResultCallback cb : waiters) cb.onResult(result);
    }

    private void resolve(String sha1, java.util.function.Supplier<byte[]> fullBytes,
                         String gameVersion, String loader, ResultCallback rawCallback) {
        // Guarantees exactly one delivery no matter what throws — an exception on one of the
        // API threads used to leave the in-flight entry stuck forever, so every later request
        // for that file queued behind it and the icon never loaded.
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        final ResultCallback callback = r -> { if (done.compareAndSet(false, true)) rawCallback.onResult(r); };
        try {
            modrinth.getVersionFromHash(sha1, currentVersion -> {
                try {
                    String projectId = currentVersion != null ? extractProjectId(currentVersion) : null;
                    if (projectId == null) { curseForgeOrNull(fullBytes, callback); return; }
                    Result result = new Result();
                    result.projectId = projectId;
                    result.currentVersionId = currentVersion.id;
                    result.source = "modrinth";

                    // Icon is fetched directly from the project, independent of the update check
                    // below — Copper's own resolveRemoteIconUrl approach (hash -> version_file ->
                    // project -> icon_url).
                    fetchModrinthIcon(projectId, result, () -> {
                        try {
                            modrinth.getVersions(projectId, nullToEmpty(gameVersion), nullToEmpty(loader), versions -> {
                                try {
                                    if (versions != null && !versions.isEmpty()) {
                                        ModVersion latest = versions.get(0);
                                        boolean upToDate = currentVersion.id != null && currentVersion.id.equals(latest.id);
                                        if (!upToDate) {
                                            ModVersion.VersionFile primary = ModDownloader.getPrimaryFile(latest);
                                            if (primary != null) {
                                                result.hasUpdate = true;
                                                result.latestVersionName = latest.versionNumber;
                                                result.latestFileUrl = primary.url;
                                                result.latestFileName = primary.filename;
                                            }
                                        }
                                    }
                                } catch (Throwable ignored) { }
                                callback.onResult(result);
                            }, e -> callback.onResult(result)); // version lookup failed — icon (if any) is still valid
                        } catch (Throwable t) { callback.onResult(result); }
                    });
                } catch (Throwable t) { callback.onResult(null); }
            }, e -> {
                try {
                    // Only a definitive "not on Modrinth" is worth a CurseForge attempt; a rate
                    // limit / network error would just burn a full-file read for nothing.
                    if (e != null && e.startsWith("Not found")) curseForgeOrNull(fullBytes, callback);
                    else callback.onResult(null);
                } catch (Throwable t) { callback.onResult(null); }
            });
        } catch (Throwable t) { callback.onResult(null); }
    }

    /**
     * CurseForge fingerprinting needs the whole file in memory. Shader packs are routinely
     * 50-100+MB, so this must never run when CurseForge is unavailable (it used to read the
     * entire file first and only then check — an OutOfMemoryError there killed the lookup
     * thread silently) and must tolerate running out of memory instead of crashing.
     */
    private void curseForgeOrNull(java.util.function.Supplier<byte[]> fullBytes, ResultCallback callback) {
        if (!CurseForgeApi.isEnabled()) { callback.onResult(null); return; }
        byte[] bytes = fullBytes.get();
        if (bytes == null) { callback.onResult(null); return; }
        fallbackToCurseForge(bytes, callback);
    }

    // ---------------------------------------------------------------------------------
    // Icon-only resolution. The Installed list only needs the project's icon URL, and must
    // not wait behind the getVersions() update lookup (or fail because of it). Results are
    // cached per file for the session; failures are not cached here (the caller decides how
    // long to back off), so a rate limit no longer blanks the icon permanently.
    // ---------------------------------------------------------------------------------

    public interface IconUrlCallback {
        void onIconUrl(String iconUrl); // null if the file wasn't recognised or the lookup failed
    }

    private static final Map<String, String> sIconUrlCache = new ConcurrentHashMap<>();
    private static final Map<String, java.util.List<IconUrlCallback>> sIconInFlight = new java.util.HashMap<>();

    public void checkIcon(Context context, DocumentFile file, IconUrlCallback callback) {
        resolveIcon(file.getUri().toString(),
                () -> { try (InputStream is = context.getContentResolver().openInputStream(file.getUri())) { return is == null ? null : sha1Hex(is); } },
                () -> readAllBytes(context, file), callback);
    }

    public void checkIcon(File file, IconUrlCallback callback) {
        resolveIcon(file.getAbsolutePath(),
                () -> { try (InputStream is = new FileInputStream(file)) { return sha1Hex(is); } },
                () -> readAllBytes(file), callback);
    }

    private interface Sha1Source { String get() throws Exception; }

    private void resolveIcon(String tag, Sha1Source sha1Source, java.util.function.Supplier<byte[]> fullBytes, IconUrlCallback callback) {
        Result full = sResultCache.get(tag);
        if (full != null && full.iconUrl != null) { callback.onIconUrl(full.iconUrl); return; }
        String cached = sIconUrlCache.get(tag);
        if (cached != null) { callback.onIconUrl(cached); return; }
        synchronized (sIconInFlight) {
            java.util.List<IconUrlCallback> waiters = sIconInFlight.get(tag);
            if (waiters != null) { waiters.add(callback); return; }
            java.util.List<IconUrlCallback> mine = new java.util.ArrayList<>();
            mine.add(callback);
            sIconInFlight.put(tag, mine);
        }
        try {
            String sha1 = sha1Source.get();
            if (sha1 == null) { flushIcon(tag, null); return; }
            modrinth.getVersionFromHash(sha1, version -> {
                try {
                    String projectId = version != null ? extractProjectId(version) : null;
                    if (projectId == null) { iconFromCurseForge(tag, fullBytes); return; }
                    modrinth.getProject(projectId, new ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
                        @Override public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult project) {
                            flushIcon(tag, project != null ? project.iconUrl : null);
                        }
                        @Override public void onError(String error) { flushIcon(tag, null); }
                    });
                } catch (Throwable t) { flushIcon(tag, null); }
            }, e -> {
                try {
                    if (e != null && e.startsWith("Not found")) iconFromCurseForge(tag, fullBytes);
                    else flushIcon(tag, null);
                } catch (Throwable t) { flushIcon(tag, null); }
            });
        } catch (Throwable t) { flushIcon(tag, null); }
    }

    private void iconFromCurseForge(String tag, java.util.function.Supplier<byte[]> fullBytes) {
        curseForgeOrNull(fullBytes, r -> flushIcon(tag, r != null ? r.iconUrl : null));
    }

    private void flushIcon(String tag, String url) {
        if (url != null && !url.isEmpty()) sIconUrlCache.put(tag, url); else url = null;
        java.util.List<IconUrlCallback> waiters;
        synchronized (sIconInFlight) { waiters = sIconInFlight.remove(tag); }
        if (waiters != null) for (IconUrlCallback cb : waiters) { try { cb.onIconUrl(url); } catch (Throwable ignored) { } }
    }

    private void fetchModrinthIcon(String projectId, Result result, Runnable onDone) {
        modrinth.getProject(projectId, new ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
            @Override public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult project) {
                if (project != null) { result.iconUrl = project.iconUrl; result.iconResolved = true; }
                onDone.run();
            }
            @Override public void onError(String error) { onDone.run(); }
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
                result.iconResolved = true;
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
        } catch (Throwable e) { return null; } // Throwable: a big shader pack can OutOfMemoryError here
    }

    private static byte[] readAllBytes(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return readAllBytes(is);
        } catch (Throwable e) { return null; }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }

    private static String sha1Hex(InputStream is) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) md.update(buf, 0, read);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
