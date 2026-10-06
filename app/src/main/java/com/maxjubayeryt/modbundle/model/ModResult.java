package com.maxjubayeryt.modbundle.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ModResult {
    @SerializedName("project_id") public String projectId;
    @SerializedName("slug")       public String slug;
    @SerializedName("title")      public String title;
    @SerializedName("description") public String description;
    @SerializedName("icon_url")   public String iconUrl;
    @SerializedName("downloads")  public int downloads;
    @SerializedName("categories") public List<String> categories;
    @SerializedName("versions")   public List<String> versions;
    @SerializedName("followers") public int followers;
    @SerializedName("body")       public String body;      // Modrinth long description (markdown)
    // Shape differs by endpoint: /search hits return an array of URL strings, /project returns an
    // array of objects. Declaring it as a typed list made every search blow up on the strings,
    // so it is kept raw and decoded by galleryImages().
    @SerializedName("gallery")    public com.google.gson.JsonElement gallery;
    public String source = "modrinth";
    @SerializedName("latest_version") public String latestVersion;

    // Set at runtime
    public boolean isInstalled = false;
    // CurseForge-only: some mod authors disable third-party API downloads
    // ("allowModDistribution" = false). The content still shows up in search/browse,
    // but installing it has to go through the CF website instead of a direct download.
    public boolean isRestricted = false;
    public String pageUrl;

    /** Gallery entries from either shape; empty if absent or not an array. */
    public List<GalleryImage> galleryImages() {
        List<GalleryImage> out = new java.util.ArrayList<>();
        if (gallery == null || !gallery.isJsonArray()) return out;
        for (com.google.gson.JsonElement e : gallery.getAsJsonArray()) {
            if (e == null || e.isJsonNull()) continue;
            GalleryImage g = new GalleryImage();
            if (e.isJsonPrimitive()) {
                g.url = e.getAsString();
            } else if (e.isJsonObject()) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                g.url = o.has("url") && !o.get("url").isJsonNull() ? o.get("url").getAsString() : null;
                g.title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : null;
                g.description = o.has("description") && !o.get("description").isJsonNull() ? o.get("description").getAsString() : null;
            }
            if (g.url != null && !g.url.isEmpty()) out.add(g);
        }
        return out;
    }

    public static class GalleryImage {
        @SerializedName("url")         public String url;
        @SerializedName("title")       public String title;
        @SerializedName("description") public String description;
    }
}
