package com.maxjubayeryt.modbundle.api;

import com.maxjubayeryt.modbundle.model.ModResult;
import com.maxjubayeryt.modbundle.model.ModVersion;
import com.maxjubayeryt.modbundle.model.SearchResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModrinthApi {

    private static final String BASE = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "ModBundle/1.0 (github.com/copperlauncher)";

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    // Installed-tab icon/name lookups fire one hash lookup + one project lookup per file.
    // Unbounded, that trips Modrinth's rate limit (HTTP 429) on any decent-sized folder;
    // the failed lookup used to be treated as "no icon exists", which permanently blanked
    // shader icons (they have no local icon to fall back on). Lookups now go through a
    // small shared gate and retry on 429/5xx instead of giving up on the first failure.
    private static final java.util.concurrent.Semaphore LOOKUP_GATE = new java.util.concurrent.Semaphore(4);
    private static final int LOOKUP_MAX_ATTEMPTS = 3;

    /** HTTP failure carrying the status code so callers can tell "not found" from "try again later". */
    public static class HttpStatusException extends IOException {
        public final int code;
        public HttpStatusException(int code) { super("HTTP " + code); this.code = code; }
    }

    private String fetchWithRetry(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= LOOKUP_MAX_ATTEMPTS; attempt++) {
            long waitMs = 0;
            try {
                LOOKUP_GATE.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
            }
            try {
                Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
                try (Response response = client.newCall(request).execute()) {
                    int code = response.code();
                    if (response.isSuccessful() && response.body() != null) return response.body().string();
                    if (code == 404) throw new HttpStatusException(404); // definitive, don't retry
                    last = new HttpStatusException(code);
                    if (code != 429 && code < 500) throw last; // other 4xx won't fix themselves
                    String retryAfter = response.header("Retry-After");
                    try { waitMs = retryAfter != null ? Long.parseLong(retryAfter.trim()) * 1000L : 0; } catch (NumberFormatException ignored) { }
                }
            } catch (HttpStatusException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            } finally {
                LOOKUP_GATE.release();
            }
            if (attempt < LOOKUP_MAX_ATTEMPTS) {
                if (waitMs <= 0) waitMs = 800L * attempt;
                try { Thread.sleep(Math.min(waitMs, 5000L)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
            }
        }
        throw last != null ? last : new IOException("Request failed");
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public interface OnSuccess<T> {
        void onSuccess(T result);
    }

    public interface OnError {
        void onError(String error);
    }

    public void searchMods(String query, String gameVersion, String loader,
                           int offset, String projectType, Callback<SearchResponse> callback) {
        new Thread(() -> {
            try {
                String type = (projectType == null || projectType.isEmpty()) ? "mod" : projectType;                String version = gameVersion != null ? gameVersion.trim() : "";
                String loaderValue = loader != null ? loader.trim() : "";                StringBuilder facets = new StringBuilder("[[\"project_type:" + type + "\"]");
                if (!version.isEmpty() && !version.equalsIgnoreCase("Any"))
                    facets.append(",[\"versions:").append(version).append("\"]");
                if (!loaderValue.isEmpty() && !loaderValue.equalsIgnoreCase("Any"))
                    facets.append(",[\"categories:").append(loaderValue).append("\"]");
                facets.append("]");
                StringBuilder url = new StringBuilder(BASE + "/search");
                url.append("?facets=").append(encode(facets.toString()));
                String q = query != null ? query.trim() : "";
                url.append("&query=").append(encode(q));
                url.append("&limit=20&offset=").append(offset);
                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("User-Agent", USER_AGENT)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { callback.onError("Server error: " + response.code()); return; }
                    SearchResponse result = gson.fromJson(response.body().string(), SearchResponse.class);
                    callback.onSuccess(result);
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    public void getVersions(String projectId, String gameVersion, String loader,
                            OnSuccess<List<ModVersion>> onSuccess, OnError onError) {
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE + "/project/" + projectId + "/version");
                boolean hasParam = false;
                if (!gameVersion.isEmpty()) {
                    url.append("?game_versions=%5B%22").append(gameVersion).append("%22%5D");
                    hasParam = true;
                }
                if (!loader.isEmpty()) {
                    url.append(hasParam ? "&" : "?");
                    url.append("loaders=%5B%22").append(loader).append("%22%5D");
                }

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("User-Agent", USER_AGENT)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("Server error: " + response.code()); return; }
                    List<ModVersion> versions = gson.fromJson(response.body().string(),
                            new TypeToken<List<ModVersion>>(){}.getType());
                    onSuccess.onSuccess(versions);
                }
            } catch (IOException e) {
                onError.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    public void getProject(String projectId, Callback<ModResult> callback) {
        new Thread(() -> {
            ModResult project;
            try {
                project = gson.fromJson(fetchWithRetry(BASE + "/project/" + projectId), ModResult.class);
            } catch (HttpStatusException e) {
                callback.onError("Server error: " + e.code); return;
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage()); return;
            } catch (RuntimeException e) {
                callback.onError("Bad response: " + e.getMessage()); return;
            }
            callback.onSuccess(project);
        }).start();
    }

    public void getVersion(String versionId, OnSuccess<ModVersion> onSuccess, OnError onError) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE + "/version/" + versionId)
                        .header("User-Agent", USER_AGENT)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("Server error: " + response.code()); return; }
                    onSuccess.onSuccess(gson.fromJson(response.body().string(), ModVersion.class));
                }
            } catch (IOException e) {
                onError.onError("Network error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Looks up which project+version a file belongs to by its SHA1 hash, regardless
     * of content type (mods, resourcepacks and shaderpacks are all resolvable this
     * way). Used for update-checking and remote icon lookup of files that don't carry
     * their own project id (resource packs, shader packs, and old mods with no
     * embedded metadata) — ported from Copper-Android's InstalledModAdapter,
     * which calls this "version_file/{hash}" endpoint before falling back to
     * CurseForge fingerprint matching.
     */
    public void getVersionFromHash(String sha1, OnSuccess<ModVersion> onSuccess, OnError onError) {
        new Thread(() -> {
            ModVersion version;
            try {
                version = gson.fromJson(fetchWithRetry(BASE + "/version_file/" + sha1 + "?algorithm=sha1"), ModVersion.class);
            } catch (HttpStatusException e) {
                onError.onError(e.code == 404 ? "Not found on Modrinth: 404" : "Modrinth error: " + e.code); return;
            } catch (IOException e) {
                onError.onError("Network error: " + e.getMessage()); return;
            } catch (RuntimeException e) {
                onError.onError("Bad response: " + e.getMessage()); return;
            }
            onSuccess.onSuccess(version);
        }).start();
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
    public void getGameVersions(boolean includeSnapshots, OnSuccess<List<String>> onSuccess, OnError onError) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url("https://api.modrinth.com/v2/tag/game_version")
                        .header("User-Agent", "ModBundle/1.0")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("HTTP " + response.code()); return; }
                    com.google.gson.reflect.TypeToken<List<com.google.gson.JsonObject>> token = new com.google.gson.reflect.TypeToken<>(){};
                    List<com.google.gson.JsonObject> tags = gson.fromJson(response.body().string(), token.getType());
                    List<String> versions = new java.util.ArrayList<>();
                    versions.add("Any");
                    for (com.google.gson.JsonObject tag : tags) {
                        String vType = tag.get("version_type").getAsString();
                        if ("release".equals(vType) || includeSnapshots) {
                            versions.add(tag.get("version").getAsString());
                        }
                    }
                    onSuccess.onSuccess(versions);
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

}