package com.modbundle.app.utils;

import android.content.Context;
import androidx.documentfile.provider.DocumentFile;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModMetadataParser {

    public static ModMetadata parse(Context ctx, DocumentFile file) {
        try {
            InputStream is = ctx.getContentResolver().openInputStream(file.getUri());
            if (is == null) return null;
            return parseStream(is);
        } catch (Exception e) { return null; }
    }

    public static ModMetadata parse(java.io.File file) {
        try {
            return parseStream(new java.io.FileInputStream(file));
        } catch (Exception e) { return null; }
    }

    private static ModMetadata parseStream(InputStream inputStream) {
        ModMetadata meta = new ModMetadata();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("fabric.mod.json".equals(name)) {
                    String json = new String(readBytes(zip), "UTF-8");
                    parseFabric(json, meta);
                    meta.loader = "fabric";
                } else if ("quilt.mod.json".equals(name) && meta.modId == null) {
                    String json = new String(readBytes(zip), "UTF-8");
                    parseFabric(json, meta);
                    meta.loader = "quilt";
                } else if ("META-INF/mods.toml".equals(name)) {
                    String toml = new String(readBytes(zip), "UTF-8");
                    parseForge(toml, meta);
                    if (meta.loader == null) meta.loader = "forge";
                } else if ("META-INF/neoforge.mods.toml".equals(name)) {
                    String toml = new String(readBytes(zip), "UTF-8");
                    parseForge(toml, meta);
                    meta.loader = "neoforge";
                }
                zip.closeEntry();
                // Don't break early - need to read all relevant entries
            }
        } catch (Exception e) { return null; }
        return meta.modId != null ? meta : null;
    }

    private static void parseFabric(String json, ModMetadata meta) {
        meta.modId = extractJsonString(json, "id");
        meta.version = extractJsonString(json, "version");
        meta.name = extractJsonString(json, "name");
        meta.iconPath = extractJsonString(json, "icon");
        // MC version from depends.minecraft
        String depends = extractJsonBlock(json, "depends");
        if (depends != null) {
            String mcVer = extractJsonString(depends, "minecraft");
            if (mcVer != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+\\.\\d+(?:\\.\\d+)?)")
                    .matcher(mcVer);
                if (m.find()) meta.mcVersion = m.group(1);
            }
        }
        // Fallback: extract MC version from version string e.g. "0.8.6+mc1.21.11" -> "1.21.11"
        if (meta.mcVersion == null && meta.version != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("mc(\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(meta.version);
            if (m.find()) meta.mcVersion = m.group(1);
        }
        // Fallback: version string starts with MC version e.g. "1.21.1-0.6.13"
        if (meta.mcVersion == null && meta.version != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+\\.\\d+(?:\\.\\d+)?)")
                .matcher(meta.version);
            if (m.find()) meta.mcVersion = m.group(1);
        }
    }

    private static void parseForge(String toml, ModMetadata meta) {
        meta.modId = extractTomlString(toml, "modId");
        meta.version = extractTomlString(toml, "version");
        meta.name = extractTomlString(toml, "displayName");
        meta.iconPath = extractTomlString(toml, "logoFile");
        // MC version from dependencies versionRange
        int depIdx = toml.indexOf("\"minecraft\"");
        if (depIdx == -1) depIdx = toml.indexOf("'minecraft'");
        if (depIdx != -1) {
            int versionRange = toml.indexOf("versionRange", depIdx);
            if (versionRange != -1) {
                String vr = extractTomlString(toml.substring(versionRange), "versionRange");
                if (vr != null) {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(\\d+\\.\\d+(?:\\.\\d+)?)")
                        .matcher(vr);
                    if (m.find()) {
                        meta.mcVersion = m.group(1);
                    }
                }
            }
        }
    }

    private static String extractJsonString(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx = json.indexOf(search);
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx + search.length());
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\n' || json.charAt(start) == '\r')) start++;
            if (start >= json.length() || json.charAt(start) != '"') return null;
            start++;
            int end = json.indexOf("\"", start);
            if (end == -1) return null;
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private static String extractJsonBlock(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx = json.indexOf(search);
            if (idx == -1) return null;
            int brace = json.indexOf("{", idx);
            if (brace == -1) return null;
            int depth = 1, pos = brace + 1;
            while (pos < json.length() && depth > 0) {
                char c = json.charAt(pos);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                pos++;
            }
            return json.substring(brace, pos);
        } catch (Exception e) { return null; }
    }

    private static String extractTomlString(String toml, String key) {
        try {
            int idx = toml.indexOf(key);
            if (idx == -1) return null;
            int eq = toml.indexOf("=", idx);
            int start = toml.indexOf("\"", eq);
            if (start == -1) return null;
            start++;
            int end = toml.indexOf("\"", start);
            if (end == -1) return null;
            return toml.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private static byte[] readBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read;
        while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
        return bos.toByteArray();
    }
}
