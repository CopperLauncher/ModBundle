package com.modbundle.app.utils;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.modbundle.app.api.ModrinthApi;
import com.modbundle.app.model.ModVersion;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ModDownloader {

    public interface DownloadCallback {
        void onProgress(String fileName, int percent);
        void onSuccess(String fileName);
        void onError(String error);
    }

    private final OkHttpClient client = new OkHttpClient();
    private final ModrinthApi api = new ModrinthApi();
    private final Context context;

    public ModDownloader(Context context) { this.context = context; }

    /** SAF download - instanceUri is the instance root, subFolder is "mods"/"resourcepacks"/"shaderpacks" */
    public void downloadMod(ModVersion.VersionFile file, Uri instanceUri, String subFolder,
                            List<ModVersion.Dependency> dependencies,
                            String gameVersion, String loader, DownloadCallback callback) {
        new Thread(() -> {
            try {
                DocumentFile targetDir = getOrCreateSubFolder(instanceUri, subFolder);
                if (targetDir == null) { callback.onError("Cannot access " + subFolder + " folder"); return; }
                downloadFileSaf(file.url, file.filename, targetDir, callback);
                // Dependencies always go in mods/
                if (dependencies != null) {
                    DocumentFile modsDir = getOrCreateSubFolder(instanceUri, "mods");
                    for (ModVersion.Dependency dep : dependencies) {
                        if ("required".equals(dep.dependencyType) && dep.projectId != null && modsDir != null) {
                            if (modsDir.findFile(dep.projectId) != null) continue;
                            api.getVersions(dep.projectId, gameVersion, loader, versions -> {
                                if (versions != null && !versions.isEmpty()) {
                                    ModVersion dv = versions.get(0);
                                    if (dv.files != null && !dv.files.isEmpty()) {
                                        ModVersion.VersionFile df = getPrimaryFile(dv.files);
                                        try { downloadFileSaf(df.url, df.filename, modsDir,
                                            new DownloadCallback() {
                                                public void onProgress(String f, int p) {}
                                                public void onSuccess(String f) { callback.onProgress("Dep: " + f, 100); }
                                                public void onError(String e) {}
                                            });
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }, e -> {});
                        }
                    }
                }
            } catch (Exception e) { callback.onError("Download failed: " + e.getMessage()); }
        }).start();
    }

    /** Legacy File-based download for Android < 11 */
    public void downloadMod(ModVersion.VersionFile file, File targetDir,
                            List<ModVersion.Dependency> dependencies,
                            String gameVersion, String loader, DownloadCallback callback) {
        new Thread(() -> {
            try {
                downloadFile(file.url, file.filename, targetDir, callback);
                if (dependencies != null) {
                    File modsDir = new File(targetDir.getParent(), "mods");
                    for (ModVersion.Dependency dep : dependencies) {
                        if ("required".equals(dep.dependencyType) && dep.projectId != null) {
                            if (new File(modsDir, dep.projectId).exists()) continue;
                            api.getVersions(dep.projectId, gameVersion, loader, versions -> {
                                if (versions != null && !versions.isEmpty()) {
                                    ModVersion dv = versions.get(0);
                                    if (dv.files != null && !dv.files.isEmpty()) {
                                        ModVersion.VersionFile df = getPrimaryFile(dv.files);
                                        try { downloadFile(df.url, df.filename, modsDir,
                                            new DownloadCallback() {
                                                public void onProgress(String f, int p) {}
                                                public void onSuccess(String f) { callback.onProgress("Dep: " + f, 100); }
                                                public void onError(String e) {}
                                            });
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }, e -> {});
                        }
                    }
                }
            } catch (Exception e) { callback.onError("Download failed: " + e.getMessage()); }
        }).start();
    }

    private DocumentFile getOrCreateSubFolder(Uri instanceUri, String folderName) {
        DocumentFile instanceDir = DocumentFile.fromTreeUri(context, instanceUri);
        if (instanceDir == null || !instanceDir.exists()) return null;
        DocumentFile sub = instanceDir.findFile(folderName);
        if (sub == null || !sub.isDirectory()) sub = instanceDir.createDirectory(folderName);
        return sub;
    }

    private void downloadFileSaf(String url, String fileName, DocumentFile dir, DownloadCallback callback) throws Exception {
        String tempName = fileName + ".download";
        DocumentFile tempFile = dir.findFile(tempName);
        if (tempFile != null) tempFile.delete();
        String mime = fileName.endsWith(".jar") ? "application/java-archive" : "application/zip";
        DocumentFile newFile = dir.createFile(mime, tempName);
        if (newFile == null) throw new Exception("Cannot create file: " + tempName);
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            long total = response.body().contentLength();
            try (InputStream in = response.body().byteStream();
                 OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                if (out == null) throw new Exception("Cannot open output stream");
                byte[] buf = new byte[8192]; long downloaded = 0; int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read); downloaded += read;
                    if (total > 0) callback.onProgress(fileName, (int)(downloaded * 100 / total));
                }
            }
        } catch (Exception e) {
            DocumentFile failed = dir.findFile(tempName);
            if (failed != null) failed.delete();
            throw e;
        }
        DocumentFile existing = dir.findFile(fileName);
        if (existing != null) existing.delete();
        if (!newFile.renameTo(fileName)) {
            DocumentFile failed = dir.findFile(tempName);
            if (failed != null) failed.delete();
            throw new Exception("Cannot rename downloaded file");
        }
        callback.onSuccess(fileName);
    }

    private void downloadFile(String url, String fileName, File dir, DownloadCallback callback) throws Exception {
        if (!dir.exists()) dir.mkdirs();
        File tempFile = new File(dir, fileName + ".download");
        if (tempFile.exists()) tempFile.delete();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("HTTP " + response.code());
            long total = response.body().contentLength();
            try (InputStream in = response.body().byteStream();
                 FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buf = new byte[8192]; long downloaded = 0; int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read); downloaded += read;
                    if (total > 0) callback.onProgress(fileName, (int)(downloaded * 100 / total));
                }
            }
        } catch (Exception e) {
            if (tempFile.exists()) tempFile.delete();
            throw e;
        }
        File finalFile = new File(dir, fileName);
        if (finalFile.exists()) finalFile.delete();
        if (!tempFile.renameTo(finalFile)) {
            tempFile.delete();
            throw new Exception("Cannot rename downloaded file");
        }
        callback.onSuccess(fileName);
    }

    private ModVersion.VersionFile getPrimaryFile(List<ModVersion.VersionFile> files) {
        for (ModVersion.VersionFile f : files) { if (f.primary) return f; }
        return files.get(0);
    }

    public static ModVersion.VersionFile getPrimaryFile(ModVersion version) {
        if (version.files == null || version.files.isEmpty()) return null;
        for (ModVersion.VersionFile f : version.files) { if (f.primary) return f; }
        return version.files.get(0);
    }
}
