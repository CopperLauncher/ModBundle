package com.modbundle.app.utils;

public class ModMetadata {
    public String modId;
    public String name;
    public String version;
    public String loader; // "fabric", "forge", "neoforge", "quilt"
    public String mcVersion;
    public String iconPath; // path inside jar

    public boolean hasUpdate = false;
    public String latestVersion;
    public String latestFileUrl;
    public String latestFileName;
}
