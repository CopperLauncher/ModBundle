package com.modbundle.app.api;

import com.modbundle.app.model.ModResult;
import com.modbundle.app.model.ModVersion;
import com.modbundle.app.model.SearchResponse;
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
                String type = (projectType == null || projectType.isEmpty()) ? "mod" : projectType;
                StringBuilder facets = new StringBuilder("[[\"project_type:" + type + "\"]");
                if (gameVersion != null && !gameVersion.isEmpty() && !gameVersion.equals("Any"))
                    facets.append(",[\"versions:").append(gameVersion).append("\"]");
                if (loader != null && !loader.isEmpty() && !loader.equals("Any"))
                    facets.append(",[\"categories:").append(loader).append("\"]");
                facets.append("]");
                StringBuilder url = new StringBuilder(BASE + "/search");
                url.append("?facets=").append(facets);
                url.append("&query=").append(query == null || query.isEmpty() ? "" : encode(query));
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
            try {
                Request request = new Request.Builder()
                        .url(BASE + "/project/" + projectId)
                        .header("User-Agent", USER_AGENT)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) { callback.onError("Server error: " + response.code()); return; }
                    callback.onSuccess(gson.fromJson(response.body().string(), ModResult.class));
                }
            } catch (IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
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