package com.modbundle.app.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ModVersion {
    @SerializedName("id")             public String id;
    @SerializedName("name")           public String name;
    @SerializedName("version_number") public String versionNumber;
    @SerializedName("version_type") public String versionType;
    @SerializedName("date_published") public String datePublished;
    @SerializedName("game_versions")  public List<String> gameVersions;
    @SerializedName("loaders")        public List<String> loaders;
    @SerializedName("files")          public List<VersionFile> files;
    @SerializedName("dependencies")   public List<Dependency> dependencies;

    public static class VersionFile {
        @SerializedName("url")      public String url;
        @SerializedName("filename") public String filename;
        @SerializedName("primary")  public boolean primary;
        @SerializedName("size")     public long size;
    }

    public static class Dependency {
        @SerializedName("project_id")    public String projectId;
        @SerializedName("version_id")    public String versionId;
        @SerializedName("dependency_type") public String dependencyType; // "required" or "optional"
    }
}
