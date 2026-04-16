package com.modbundle.app.model;

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
    public String source = "modrinth";
    @SerializedName("latest_version") public String latestVersion;

    // Set at runtime
    public boolean isInstalled = false;
}
