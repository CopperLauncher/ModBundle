package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Disk-cached downloader for remote content icons (Modrinth icon_url / CurseForge
 * logo thumbnailUrl). Ported and consolidated from CopperLauncher/Copper-Android's
 * modloaders.modpacks.imagecache package (ModIconCache + ReadFromDiskTask +
 * DownloadImageTask + IconCacheJanitor), which Copper uses to keep its
 * "Browse/Manage Content" mod/resourcepack/shaderpack icons responsive without
 * re-downloading them on every list scroll.
 *
 * Icons are cached to disk keyed by a caller-supplied tag (typically the SHA1 or
 * murmur2 fingerprint of the installed file, or the Modrinth/CurseForge project id
 * for browse results), downscaled to a max of 256px, and the cache directory is
 * kept under a size budget by a background janitor.
 */
public class RemoteIconCache {

    private static final long CACHE_SIZE_LIMIT = 104_857_600L; // 100 MB
    private static final long CACHE_BRINGDOWN  = 52_428_800L;  // 50 MB
    private static final float BITMAP_FINAL_DIMENSION = 256f;

    public interface IconCallback {
        void onIcon(Bitmap bitmap); // null if not found / failed
    }

    private static volatile RemoteIconCache sInstance;

    public static RemoteIconCache get(Context context) {
        if (sInstance == null) {
            synchronized (RemoteIconCache.class) {
                if (sInstance == null) sInstance = new RemoteIconCache(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private final Executor pool = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();
    private final File cacheDir;
    private volatile boolean janitorRan = false;

    private RemoteIconCache(Context context) {
        cacheDir = new File(context.getCacheDir(), "remote_icons");
        if (!cacheDir.exists()) cacheDir.mkdirs();
    }

    /**
     * Fetches an icon for the given tag+url, reading from the on-disk cache first.
     * Callback is always invoked on the main thread.
     */
    public void getIcon(String tag, String url, IconCallback callback) {
        if (url == null || url.isEmpty() || tag == null || tag.isEmpty()) {
            mainHandler.post(() -> callback.onIcon(null));
            return;
        }
        pool.execute(() -> {
            File cacheFile = new File(cacheDir, sanitize(tag) + ".ico");
            if (cacheFile.isFile() && cacheFile.canRead()) {
                Bitmap bmp = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (bmp != null) {
                    mainHandler.post(() -> callback.onIcon(bmp));
                    return;
                }
            }
            Bitmap downloaded = downloadAndCache(url, cacheFile);
            mainHandler.post(() -> callback.onIcon(downloaded));
            runJanitorIfNeeded();
        });
    }

    private Bitmap downloadAndCache(String url, File cacheFile) {
        int retries = 0;
        while (retries < 3) {
            try {
                Request request = new Request.Builder().url(url).build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        retries++;
                        continue;
                    }
                    try (InputStream in = response.body().byteStream();
                         OutputStream out = new FileOutputStream(cacheFile)) {
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                    }
                }
                Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (bitmap == null) return null;
                return downscaleAndPersist(bitmap, cacheFile);
            } catch (IOException e) {
                retries++;
            }
        }
        return null;
    }

    private Bitmap downscaleAndPersist(Bitmap bitmap, File cacheFile) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        if (w <= BITMAP_FINAL_DIMENSION && h <= BITMAP_FINAL_DIMENSION) return bitmap;
        float ratio = Math.min(BITMAP_FINAL_DIMENSION / w, BITMAP_FINAL_DIMENSION / h);
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, (int) (w * ratio), (int) (h * ratio), true);
        if (resized != bitmap) {
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                resized.compress(Bitmap.CompressFormat.PNG, 90, fos);
            } catch (IOException ignored) {
            }
        }
        return resized;
    }

    /** Keeps the icon cache directory under CACHE_SIZE_LIMIT, evicting the least-recently-modified files first. */
    private void runJanitorIfNeeded() {
        if (janitorRan) return;
        synchronized (this) {
            if (janitorRan) return;
            janitorRan = true;
        }
        pool.execute(() -> {
            File[] files = cacheDir.listFiles();
            if (files == null) return;
            long total = 0;
            for (File f : files) total += f.length();
            if (total < CACHE_SIZE_LIMIT) return;
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : files) {
                if (total < CACHE_BRINGDOWN) break;
                long size = f.length();
                if (f.delete()) total -= size;
            }
        });
    }

    private static String sanitize(String tag) {
        return tag.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
