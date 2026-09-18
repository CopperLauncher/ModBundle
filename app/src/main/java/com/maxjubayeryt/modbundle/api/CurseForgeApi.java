package com.maxjubayeryt.modbundle.api;

import com.maxjubayeryt.modbundle.utils.KeyUtils;
import com.maxjubayeryt.modbundle.model.ModResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CurseForgeApi {
    private static final String BASE = "https://api.curseforge.com/v1";
    private static final String API_KEY = KeyUtils.decode(new byte[]{0x50, 0x00, 0x00});
    private static final boolean ENABLED = API_KEY != null && !API_KEY.contains("\u0000") && API_KEY.length() > 10;
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public static boolean isEnabled() {
        return ENABLED;
    }

    public void searchMods(String query, String gameVersion, String loader,
                           int offset, String projectType, ModrinthApi.OnSuccess<List<ModResult>> onSuccess,
                           ModrinthApi.OnError onError) {
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                int classId = 6; // mods
                if ("resourcepack".equals(projectType)) classId = 12;
                else if ("shader".equals(projectType)) classId = 6552;
                StringBuilder url = new StringBuilder(BASE + "/mods/search?gameId=432&classId=" + classId + "&pageSize=20&index=" + offset);
                if (query != null && !query.isEmpty()) url.append("&searchFilter=").append(encode(query));
                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any"))
                    url.append("&gameVersion=").append(encode(gameVersion));
                int loaderCode = 0;
                if (loader != null && !loader.isEmpty() && !loader.equals("Any")) {
                    loaderCode = loaderType(loader);
                    if (loaderCode != 0) {
                        url.append("&modLoaderType=").append(loaderCode);
                    }
                }
                url.append("&sortField=2&sortOrder=desc");

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonArray data = json.getAsJsonArray("data");
                    List<ModResult> results = new ArrayList<>();
                    for (int i = 0; i < data.size(); i++) {
                        JsonObject mod = data.get(i).getAsJsonObject();
                        ModResult r = new ModResult();
                        r.projectId = mod.get("id").getAsString();
                        r.title = mod.get("name").getAsString();
                        r.description = mod.get("summary").getAsString();
                        JsonObject logo = mod.has("logo") && !mod.get("logo").isJsonNull()
                                ? mod.getAsJsonObject("logo") : null;
                        r.iconUrl = logo != null ? logo.get("thumbnailUrl").getAsString() : null;
                        r.downloads = mod.has("downloadCount") ? mod.get("downloadCount").getAsInt() : 0;
                        r.source = "curseforge";
                        // "allowModDistribution" is null for most mods (meaning allowed) and only
                        // explicitly false for the ones whose author opted out of third-party API
                        // downloads. Gson maps a JSON null through as isJsonNull(), not a Java null
                        // boolean, so it has to be checked before calling getAsBoolean().
                        if (mod.has("allowModDistribution") && !mod.get("allowModDistribution").isJsonNull()) {
                            r.isRestricted = !mod.get("allowModDistribution").getAsBoolean();
                        }
                        if (mod.has("links") && !mod.get("links").isJsonNull()) {
                            JsonObject links = mod.getAsJsonObject("links");
                            if (links.has("websiteUrl") && !links.get("websiteUrl").isJsonNull()) {
                                r.pageUrl = links.get("websiteUrl").getAsString();
                            }
                        }
                        // Client-side backstop for the loader filter: CurseForge's own docs
                        // say modLoaderType is only honored server-side alongside a specific
                        // gameVersion, so leaving gameVersion at "Any" while filtering by
                        // loader was silently returning every loader's mods. Checking each
                        // result's own file index here is correct regardless of what the
                        // server actually did with the query params.
                        if (loaderCode != 0 && mod.has("latestFilesIndexes")) {
                            boolean matchesLoader = false;
                            JsonArray fileIndexes = mod.getAsJsonArray("latestFilesIndexes");
                            for (int j = 0; j < fileIndexes.size(); j++) {
                                JsonObject idx = fileIndexes.get(j).getAsJsonObject();
                                if (idx.has("modLoader") && !idx.get("modLoader").isJsonNull()
                                        && idx.get("modLoader").getAsInt() == loaderCode) {
                                    matchesLoader = true; break;
                                }
                            }
                            if (!matchesLoader) continue;
                        }
                        results.add(r);
                    }
                    onSuccess.onSuccess(results);
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

    public void getDownloadUrl(String modId, String fileId,
                               ModrinthApi.OnSuccess<String> onSuccess,
                               ModrinthApi.OnError onError) {
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE + "/mods/" + modId + "/files/" + fileId + "/download-url")
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    onSuccess.onSuccess(json.get("data").getAsString());
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

    public void getLatestFile(String modId, String gameVersion, String loader,
                              ModrinthApi.OnSuccess<JsonObject> onSuccess,
                              ModrinthApi.OnError onError) {
        getFiles(modId, gameVersion, loader, files -> {
            if (files == null || files.isEmpty()) { onError.onError("No files found"); return; }
            onSuccess.onSuccess(files.get(0));
        }, onError);
    }

    /**
     * Returns every matching file for a CF mod/resourcepack/shaderpack, newest first —
     * unlike {@link #getLatestFile}, which only ever returns the single newest one. That
     * was the whole reason CF content only ever showed a single install option: the
     * version-picker dialog was built from one file instead of the full list this
     * returns.
     */
    public void getFiles(String modId, String gameVersion, String loader,
                         ModrinthApi.OnSuccess<java.util.List<JsonObject>> onSuccess,
                         ModrinthApi.OnError onError) {
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE + "/mods/" + modId + "/files?pageSize=20");
                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any"))
                    url.append("&gameVersion=").append(encode(gameVersion));
                if (loader != null && !loader.isEmpty() && !loader.equals("Any")) {
                    int loaderCode = loaderType(loader);
                    if (loaderCode != 0) {
                        url.append("&modLoaderType=").append(loaderCode);
                    }
                }

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonArray files = json.getAsJsonArray("data");
                    java.util.List<JsonObject> result = new ArrayList<>();
                    for (int i = 0; i < files.size(); i++) result.add(files.get(i).getAsJsonObject());
                    onSuccess.onSuccess(result);
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

    /**
     * Fingerprint-based lookup (murmur2, see {@link com.maxjubayeryt.modbundle.utils.Murmur2}), used
     * as the fallback when a file isn't recognised by Modrinth — mainly old Forge mods and
     * CurseForge-only resource/shader packs. Ported from Copper-Android's
     * InstalledModAdapter#resolveRemoteIconUrl / checkUpdateForEntry fingerprint chain.
     * Returns the matched CurseForge mod id, or null if there was no exact match.
     */
    public void getFingerprintMatch(long fingerprint, ModrinthApi.OnSuccess<JsonObject> onSuccess,
                                    ModrinthApi.OnError onError) {
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                JsonArray fingerprints = new JsonArray();
                fingerprints.add(fingerprint);
                JsonObject body = new JsonObject();
                body.add("fingerprints", fingerprints);

                Request request = new Request.Builder()
                        .url(BASE + "/fingerprints")
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .post(okhttp3.RequestBody.create(body.toString(),
                                okhttp3.MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    if (!json.has("data")) { onError.onError("No data"); return; }
                    JsonObject data = json.getAsJsonObject("data");
                    JsonArray exactMatches = data.has("exactMatches") ? data.getAsJsonArray("exactMatches") : null;
                    if (exactMatches == null || exactMatches.size() == 0) { onError.onError("No match"); return; }
                    onSuccess.onSuccess(exactMatches.get(0).getAsJsonObject());
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

    /** Fetches a CurseForge project by numeric mod id — used to read its logo thumbnail and latest files. */
    public void getMod(int modId, ModrinthApi.OnSuccess<JsonObject> onSuccess, ModrinthApi.OnError onError) {
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(BASE + "/mods/" + modId)
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    if (!json.has("data")) { onError.onError("No data"); return; }
                    onSuccess.onSuccess(json.getAsJsonObject("data"));
                }
            } catch (Exception e) { onError.onError(e.getMessage()); }
        }).start();
    }

    private int loaderType(String loader) {
        switch (loader.toLowerCase()) {
            case "forge": return 1;
            case "fabric": return 4;
            case "quilt": return 5;
            case "neoforge": return 6;
            default: return 0;
        }
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
