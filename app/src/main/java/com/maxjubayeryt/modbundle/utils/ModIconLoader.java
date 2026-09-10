package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;
import androidx.documentfile.provider.DocumentFile;
import com.maxjubayeryt.modbundle.R;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModIconLoader {

    public enum FileType { MOD, RESOURCEPACK, SHADER }

    // Content whose embedded/local icon couldn't be found is retried against
    // Modrinth/CurseForge at most once per session and remembered here, so a
    // RecyclerView scrolling back and forth doesn't refire the same lookups —
    // mirrors Copper's InstalledModAdapter#mIconCheckedNoResult behaviour.
    private static final java.util.Set<String> sNoRemoteIcon =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final ContentUpdateChecker sUpdateChecker = new ContentUpdateChecker();
    // A RecyclerView rebind (scrolling, or any notifyDataSetChanged()) re-binds every
    // visible row at once; spawning a raw `new Thread()` per icon load meant a burst of
    // dozens of threads each time, which is itself expensive and was a real contributor
    // to the lag after disabling/updating a mod. Icon loads now go through this small
    // bounded pool instead — mirrors Copper's own sUpdateCheckExecutor pattern.
    private static final java.util.concurrent.ExecutorService sIconExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(3);

    /** Load icon from a File path, falling back to Modrinth/CurseForge if there's no embedded one. */
    public static void load(Context ctx, java.io.File file, FileType type, ImageView target) {
        sIconExecutor.execute(() -> {
            try {
                Bitmap bmp = extractIcon(ctx, new java.io.FileInputStream(file), type, file.getName());
                if (bmp != null) {
                    postBitmap(target, bmp);
                } else {
                    tryRemoteIcon(ctx, file.getAbsolutePath(), target, type,
                            cb -> sUpdateChecker.check(file, "", "", cb));
                }
            } catch (Exception e) {
                setDefault(ctx, target, type);
            }
        });
    }

    /** Load icon from a SAF DocumentFile, falling back to Modrinth/CurseForge if there's no embedded one. */
    public static void load(Context ctx, DocumentFile file, FileType type, ImageView target) {
        sIconExecutor.execute(() -> {
            try {
                InputStream is = ctx.getContentResolver().openInputStream(file.getUri());
                if (is == null) { setDefault(ctx, target, type); return; }
                String name = file.getName() != null ? file.getName() : "";
                Bitmap bmp = extractIcon(ctx, is, type, name);
                if (bmp != null) {
                    postBitmap(target, bmp);
                } else {
                    tryRemoteIcon(ctx, file.getUri().toString(), target, type,
                            cb -> sUpdateChecker.check(ctx, file, "", "", cb));
                }
            } catch (Exception e) {
                setDefault(ctx, target, type);
            }
        });
    }

    private interface CheckInvoker { void invoke(ContentUpdateChecker.ResultCallback cb); }

    /**
     * Remote icon fallback shared by both load() overloads: hashes the file (inside
     * ContentUpdateChecker) to identify it on Modrinth, falling back to CurseForge
     * fingerprint matching, then downloads+disk-caches the resolved icon_url/logo via
     * RemoteIconCache. This is what actually makes shader packs and resource packs
     * (which never carry an embedded icon the way mod jars do) show real icons instead
     * of the generic placeholder — ported from Copper-Android's icon resolution chain.
     */
    private static void tryRemoteIcon(Context ctx, String tag, ImageView target, FileType type, CheckInvoker invoker) {
        if (sNoRemoteIcon.contains(tag)) { setDefault(ctx, target, type); return; }
        invoker.invoke(result -> {
            if (result == null || result.iconUrl == null || result.iconUrl.isEmpty()) {
                sNoRemoteIcon.add(tag);
                setDefault(ctx, target, type);
                return;
            }
            RemoteIconCache.get(ctx).getIcon(tag, result.iconUrl, bitmap -> {
                if (bitmap != null) target.setImageBitmap(bitmap);
                else { sNoRemoteIcon.add(tag); setDefault(ctx, target, type); }
            });
        });
    }

    private static void postBitmap(ImageView target, Bitmap bmp) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> target.setImageBitmap(bmp));
    }

    private static Bitmap extractIcon(Context ctx, InputStream inputStream, FileType type, String fileName) {
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            // For shaders, no icon
            if (type == FileType.SHADER) return null;

            // For resource packs, look for pack.png
            if (type == FileType.RESOURCEPACK) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if ("pack.png".equals(entry.getName())) {
                        return BitmapFactory.decodeStream(zip);
                    }
                    zip.closeEntry();
                }
                return null;
            }

            // For mods, find icon path from fabric.mod.json or mods.toml
            // First pass: find the icon path
            String iconPath = null;
            java.util.Map<String, byte[]> entries = new java.util.HashMap<>();
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                // Read fabric.mod.json
                if ("fabric.mod.json".equals(entryName)) {
                    byte[] data = readBytes(zip);
                    String json = new String(data, "UTF-8");
                    iconPath = extractFabricIcon(json);
                    entries.put(entryName, data);
                } else if ("META-INF/mods.toml".equals(entryName) || "META-INF/neoforge.mods.toml".equals(entryName)) {
                    byte[] data = readBytes(zip);
                    String toml = new String(data, "UTF-8");
                    if (iconPath == null) iconPath = extractForgeIcon(toml);
                    entries.put(entryName, data);
                } else if (entryName.endsWith(".png")) {
                    entries.put(entryName, readBytes(zip));
                }
                zip.closeEntry();
            }

            // Try to get bitmap from found icon path
            if (iconPath != null) {
                // Strip leading slash
                if (iconPath.startsWith("/")) iconPath = iconPath.substring(1);
                byte[] iconData = entries.get(iconPath);
                if (iconData != null) {
                    return BitmapFactory.decodeByteArray(iconData, 0, iconData.length);
                }
            }

            // Fallback: try common icon paths
            for (String path : new String[]{"icon.png", "assets/icon.png", "logo.png"}) {
                byte[] data = entries.get(path);
                if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length);
            }

            // Last fallback: any PNG in assets/modid/
            for (java.util.Map.Entry<String, byte[]> e : entries.entrySet()) {
                if (e.getKey().endsWith(".png") && e.getKey().startsWith("assets/")) {
                    return BitmapFactory.decodeByteArray(e.getValue(), 0, e.getValue().length);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static String extractFabricIcon(String json) {
        try {
            // Simple JSON parse for "icon" field
            int idx = json.indexOf("\"icon\"");
            if (idx == -1) return null;
            int colon = json.indexOf(":", idx);
            int start = json.indexOf("\"", colon) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return null; }
    }

    private static String extractForgeIcon(String toml) {
        try {
            // Look for logoFile = "..."
            int idx = toml.indexOf("logoFile");
            if (idx == -1) return null;
            int eq = toml.indexOf("=", idx);
            int start = toml.indexOf("\"", eq) + 1;
            int end = toml.indexOf("\"", start);
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

    private static void setDefault(Context ctx, ImageView target, FileType type) {
        int res = type == FileType.SHADER ? R.drawable.ic_shader_default
                : type == FileType.RESOURCEPACK ? R.drawable.ic_respack_default
                : R.drawable.ic_mod_default;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> target.setImageResource(res));
    }
}
