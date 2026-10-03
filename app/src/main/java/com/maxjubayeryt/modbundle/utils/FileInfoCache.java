package com.maxjubayeryt.modbundle.utils;

import androidx.documentfile.provider.DocumentFile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name/size of SAF documents captured by the single bulk directory query that builds the
 * Installed list. DocumentFile.getName()/length() are each a synchronous ContentResolver
 * round trip to the DocumentsProvider, so reading them per row on the main thread (and
 * twice per comparison while sorting) is what made large folders take seconds to appear.
 * Anything not in the cache simply falls back to asking the DocumentFile.
 */
public final class FileInfoCache {

    private static final class Info {
        final String name;
        final long size;
        Info(String name, long size) { this.name = name; this.size = size; }
    }

    private static final Map<String, Info> sCache = new ConcurrentHashMap<>();

    private FileInfoCache() {}

    public static void put(String uri, String name, long size) {
        if (uri != null && name != null) sCache.put(uri, new Info(name, size));
    }

    public static String name(DocumentFile file) {
        Info i = sCache.get(file.getUri().toString());
        return i != null ? i.name : file.getName();
    }

    public static long size(DocumentFile file) {
        Info i = sCache.get(file.getUri().toString());
        return i != null ? i.size : file.length();
    }
}
