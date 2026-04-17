package com.modbundle.app.api;

import com.modbundle.app.utils.KeyUtils;
import com.modbundle.app.model.ModResult;
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
                if (loader != null && !loader.isEmpty() && !loader.equals("Any"))
                    url.append("&modLoaderType=").append(loaderType(loader));
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
        if (!ENABLED) { onError.onError("CurseForge support unavailable"); return; }
        new Thread(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE + "/mods/" + modId + "/files?pageSize=10");
                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any"))
                    url.append("&gameVersion=").append(encode(gameVersion));
                if (loader != null && !loader.isEmpty() && !loader.equals("Any"))
                    url.append("&modLoaderType=").append(loaderType(loader));

                Request request = new Request.Builder()
                        .url(url.toString())
                        .header("x-api-key", API_KEY)
                        .header("Accept", "application/json")
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { onError.onError("CF Error: " + response.code()); return; }
                    JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
                    JsonArray files = json.getAsJsonArray("data");
                    if (files.size() == 0) { onError.onError("No files found"); return; }
                    onSuccess.onSuccess(files.get(0).getAsJsonObject());
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
