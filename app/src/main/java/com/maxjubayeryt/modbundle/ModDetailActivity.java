package com.maxjubayeryt.modbundle;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.maxjubayeryt.modbundle.api.CurseForgeApi;
import com.maxjubayeryt.modbundle.api.ModrinthApi;
import com.maxjubayeryt.modbundle.model.ModResult;
import com.maxjubayeryt.modbundle.model.ModVersion;
import com.maxjubayeryt.modbundle.ui.VersionAdapter;
import com.maxjubayeryt.modbundle.utils.ModDownloader;
import com.maxjubayeryt.modbundle.utils.PrefManager;
import android.os.Handler;
import android.os.Looper;
import java.util.List;

public class ModDetailActivity extends AppCompatActivity {

    public static final String EXTRA_MOD = "mod_json";
    public static final String EXTRA_PROJECT_TYPE = "project_type";
    public static final String EXTRA_SOURCE = "source";

    private ModResult mod;
    private String projectType;
    private ModDownloader downloader;
    private PrefManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ModrinthApi api = new ModrinthApi();
    private final CurseForgeApi cfApi = new CurseForgeApi();
    private String source;
    private String gameVersion = "";
    private String loader = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.maxjubayeryt.modbundle.utils.ThemeManager.apply(this);
        setContentView(R.layout.activity_mod_detail);

        downloader = new ModDownloader(this);
        prefs = new PrefManager(this);

        String modJson = getIntent().getStringExtra(EXTRA_MOD);
        projectType = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        source = getIntent().getStringExtra(EXTRA_SOURCE);
        gameVersion = getIntent().getStringExtra("game_version") != null ? getIntent().getStringExtra("game_version") : "";
        loader = getIntent().getStringExtra("loader") != null ? getIntent().getStringExtra("loader") : "";
        
        if (modJson == null) { finish(); return; }
        try {
            mod = new com.google.gson.Gson().fromJson(modJson, ModResult.class);
        } catch (Exception e) { finish(); return; }

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        TextView tvTitleToolbar = findViewById(R.id.tv_detail_title);
        if (tvTitleToolbar != null) tvTitleToolbar.setText(mod.title);

        ImageView icon = findViewById(R.id.detail_icon);
        if (icon != null) {
            icon.setImageResource(R.drawable.ic_mod_default);
            if (mod.iconUrl != null && !mod.iconUrl.isEmpty() && mod.projectId != null) {
                com.maxjubayeryt.modbundle.utils.RemoteIconCache.get(this)
                        .getIcon(mod.source + ":" + mod.projectId, mod.iconUrl, bitmap -> {
                            if (bitmap != null) icon.setImageBitmap(bitmap);
                        });
            }
        }

        TextView tvTitleMain = findViewById(R.id.detail_title);
        if (tvTitleMain != null) tvTitleMain.setText(mod.title);

        TextView tvBadge = findViewById(R.id.detail_type_badge);
        if (tvBadge != null) {
            String typeLabel = "resourcepack".equals(projectType) ? "Resource Pack"
                             : "shader".equals(projectType) ? "Shader" : "Mod";
            tvBadge.setText(typeLabel);
        }

        TextView tvDesc = findViewById(R.id.detail_description);
        if (tvDesc != null) tvDesc.setText(mod.description);

        TextView tvDownloads = findViewById(R.id.detail_downloads);
        if (tvDownloads != null) tvDownloads.setText(formatNumber(mod.downloads));

        TextView tvFollowers = findViewById(R.id.detail_followers);
        if (tvFollowers != null) tvFollowers.setText(formatNumber(mod.followers));

        ChipGroup chipGroup = findViewById(R.id.detail_categories);
        if (chipGroup != null && mod.categories != null) {
            for (String cat : mod.categories) {
                Chip chip = new Chip(this);
                chip.setText(cat);
                chip.setChipBackgroundColorResource(android.R.color.transparent);
                chip.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.primary(chip));
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(com.maxjubayeryt.modbundle.utils.ThemeColors.primary(chip)));
                chip.setChipStrokeWidth(1f);
                chip.setClickable(false);
                chipGroup.addView(chip);
            }
        }

        api.getProject(mod.projectId, new com.maxjubayeryt.modbundle.api.ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
            public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult fullMod) {
                handler.post(() -> {
                    if (tvFollowers != null) tvFollowers.setText(formatNumber(fullMod.followers));
                    if (tvDownloads != null) tvDownloads.setText(formatNumber(fullMod.downloads));
                });
            }
            public void onError(String error) {}
        });

        ProgressBar progress = findViewById(R.id.detail_versions_progress);
        RecyclerView versionsRecycler = findViewById(R.id.detail_versions_recycler);
        if (versionsRecycler != null) {
            versionsRecycler.setLayoutManager(new LinearLayoutManager(this));
            if (progress != null) progress.setVisibility(View.VISIBLE);

            if ("curseforge".equals(source)) {
                cfApi.getFiles(mod.projectId, "", "", files -> {
                    handler.post(() -> {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (files == null || files.isEmpty()) {
                            showCurseForgeRestrictedDialog();
                            return;
                        }
                        // Every returned file becomes its own entry instead of only ever
                        // showing the single newest one — that was the whole reason CF
                        // content only ever had one version to pick from.
                        java.util.List<ModVersion> versionList = new java.util.ArrayList<>();
                        for (com.google.gson.JsonObject fileObj : files) {
                            if (!fileObj.has("id") || !fileObj.has("fileName")) continue;
                            String fileId = fileObj.get("id").getAsString();
                            String fileName = fileObj.get("fileName").getAsString();
                            if (fileId == null || fileId.isEmpty() || fileName == null || fileName.isEmpty()) continue;
                            ModVersion v = new ModVersion();
                            v.id = fileId; // stashed so the click handler below can resolve this exact file's download url
                            v.versionNumber = fileName;
                            v.versionType = "release";
                            v.dependencies = new java.util.ArrayList<>();
                            ModVersion.VersionFile file = new ModVersion.VersionFile();
                            file.filename = fileName;
                            file.primary = true;
                            // file.url is intentionally left unset here: CF only resolves a
                            // real download link one file at a time, so it's fetched lazily
                            // below for whichever version the user actually taps, instead of
                            // firing one request per file up front.
                            v.files = java.util.Arrays.asList(file);
                            versionList.add(v);
                        }
                        if (versionList.isEmpty()) {
                            showCurseForgeRestrictedDialog();
                            return;
                        }
                        VersionAdapter adapter = new VersionAdapter(versionList, (version, file) -> {
                            com.maxjubayeryt.modbundle.ui.M3ProgressDialog resolving = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
                            resolving.setMessage("Resolving download link\u2026");
                            resolving.setCancelable(true);
                            resolving.show();
                            cfApi.getDownloadUrl(mod.projectId, version.id, url -> handler.post(() -> {
                                resolving.dismiss();
                                if (url == null || url.isEmpty()) { showCurseForgeRestrictedDialog(); return; }
                                file.url = url;
                                startDownload(version, file);
                            }), err -> handler.post(() -> { resolving.dismiss(); showCurseForgeRestrictedDialog(); }));
                        });
                        versionsRecycler.setAdapter(adapter);
                    });
                }, error -> handler.post(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    // A 403 here (as opposed to a network/parsing failure) almost always means
                    // this mod has third-party downloads disabled — CF's files endpoint itself
                    // refuses the request rather than returning files with a null downloadUrl.
                    if (error != null && error.contains("403")) showCurseForgeRestrictedDialog();
                    else Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show();
                }));
            } else {
                api.getVersions(mod.projectId, gameVersion, loader, versions -> {
                    handler.post(() -> {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (versions == null || versions.isEmpty()) {
                            Toast.makeText(this, "No compatible versions found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // Every content version type (release/beta/alpha) is always shown here —
                        // that release-channel field on Modrinth versions is unrelated to
                        // Minecraft *snapshot* game versions, so it must never be gated by the
                        // "include snapshots" toggle. Snapshots only affect which Minecraft game
                        // versions show up in the version spinner (see getGameVersions above).
                        List<ModVersion> filtered = new java.util.ArrayList<>(versions);
                        VersionAdapter adapter = new VersionAdapter(filtered, (version, file) ->
                            confirmDependenciesAndStartDownload(version, file));
                        versionsRecycler.setAdapter(adapter);
                    });
                }, error -> handler.post(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show();
                }));
            }
        }
    }

    /**
     * Some CurseForge mod/resourcepack/shaderpack authors disable third-party API
     * downloads ("Allow third party download" off in their CF project settings) —
     * their files still show up in search, but the download-url endpoint returns
     * nothing for them. Ported from Copper-Android's own handling of this: instead
     * of just failing, send the user to the mod's CurseForge page to download it
     * manually from there.
     */
    private void showCurseForgeRestrictedDialog() {
        String url = mod != null && mod.pageUrl != null && !mod.pageUrl.isEmpty()
                ? mod.pageUrl
                : "https://www.curseforge.com/minecraft/search?search=" + android.net.Uri.encode(mod != null ? mod.title : "");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Download restricted")
                .setMessage("The author of this content has disabled downloads through third-party apps like ModBundle. You can still get it from the CurseForge website.")
                .setPositiveButton("Open CurseForge", (d, w) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownload(ModVersion version, ModVersion.VersionFile file) {
        String subFolder = "resourcepack".equals(projectType) ? "resourcepacks"
                         : "shader".equals(projectType) ? "shaderpacks" : "mods";

        com.maxjubayeryt.modbundle.ui.M3ProgressDialog pDialog = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        pDialog.setTitle("Installing " + mod.title);
        pDialog.setMessage("Downloading\u2026");
        pDialog.setProgressStyle(com.maxjubayeryt.modbundle.ui.M3ProgressDialog.STYLE_HORIZONTAL);
        pDialog.setMax(100);
        pDialog.setCancelable(false);
        pDialog.show();

        ModDownloader.DownloadCallback callback = new ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {
                handler.post(() -> { pDialog.setMessage(fileName); pDialog.setProgress(percent); });
            }
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    pDialog.dismiss();
                    Toast.makeText(ModDetailActivity.this, mod.title + " installed!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    pDialog.dismiss();
                    Toast.makeText(ModDetailActivity.this, "Install failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };

        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, subFolder,
                version.dependencies, "", "", callback);
        } else {
            java.io.File instanceDir = prefs.getInstanceUri() != null
                ? new java.io.File(prefs.getInstanceUri().getPath()) : null;
            if (instanceDir == null) { pDialog.dismiss(); return; }
            java.io.File targetDir = new java.io.File(instanceDir, subFolder);
            if (!targetDir.exists()) targetDir.mkdirs();
            downloader.downloadMod(file, targetDir, version.dependencies, "", "", callback);
        }
    }

    private void confirmDependenciesAndStartDownload(ModVersion version, ModVersion.VersionFile file) {
        if (version.dependencies == null || version.dependencies.isEmpty()) {
            startDownload(version, file, version.dependencies);
            return;
        }

        java.util.List<ModVersion.Dependency> deps = new java.util.ArrayList<>();
        java.util.List<Boolean> checked = new java.util.ArrayList<>();
        for (ModVersion.Dependency dep : version.dependencies) {
            if (dep == null || (dep.projectId == null && dep.versionId == null)) continue;
            deps.add(dep);
            String type = dep.dependencyType != null ? dep.dependencyType : "required";
            checked.add("required".equals(type));
        }

        if (deps.isEmpty()) {
            startDownload(version, file, version.dependencies);
            return;
        }

        // Fetch project names for all deps, then show dialog
        String[] labels = new String[deps.size()];
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(deps.size());
        for (int i = 0; i < deps.size(); i++) {
            final int idx = i;
            ModVersion.Dependency dep = deps.get(idx);
            String type = dep.dependencyType != null ? dep.dependencyType : "required";
            String prefix = "required".equals(type) ? "Required: " : "Optional: ";
            if (dep.projectId != null) {
                api.getProject(dep.projectId, new com.maxjubayeryt.modbundle.api.ModrinthApi.Callback<com.maxjubayeryt.modbundle.model.ModResult>() {
                    public void onSuccess(com.maxjubayeryt.modbundle.model.ModResult result) {
                        labels[idx] = prefix + (result != null && result.title != null ? result.title : dep.projectId);
                        if (remaining.decrementAndGet() == 0) handler.post(() -> showDepsDialog(version, file, deps, labels, checked));
                    }
                    public void onError(String e) {
                        labels[idx] = prefix + dep.projectId;
                        if (remaining.decrementAndGet() == 0) handler.post(() -> showDepsDialog(version, file, deps, labels, checked));
                    }
                });
            } else {
                // Only versionId available — use it as fallback label
                labels[idx] = prefix + dep.versionId;
                if (remaining.decrementAndGet() == 0) handler.post(() -> showDepsDialog(version, file, deps, labels, checked));
            }
        }
    }

    private void showDepsDialog(ModVersion version, ModVersion.VersionFile file,
                                java.util.List<ModVersion.Dependency> deps,
                                String[] labels, java.util.List<Boolean> checked) {
        boolean[] selected = new boolean[checked.size()];
        for (int i = 0; i < checked.size(); i++) selected[i] = checked.get(i);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select dependencies to install")
            .setMultiChoiceItems(labels, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
            .setPositiveButton("Install selected", (d, w) -> {
                java.util.List<ModVersion.Dependency> selectedDeps = new java.util.ArrayList<>();
                for (int i = 0; i < deps.size(); i++) {
                    if (selected[i]) selectedDeps.add(deps.get(i));
                }
                startDownload(version, file, selectedDeps);
            })
            .setNeutralButton("Install without deps", (d, w) -> startDownload(version, file, new java.util.ArrayList<>()))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startDownload(ModVersion version, ModVersion.VersionFile file, java.util.List<ModVersion.Dependency> dependencies) {
        String subFolder = "resourcepack".equals(projectType) ? "resourcepacks"
                         : "shader".equals(projectType) ? "shaderpacks" : "mods";
        // Use actual version/loader from the selected mod version
        if (version.gameVersions != null && !version.gameVersions.isEmpty())
            gameVersion = version.gameVersions.get(0);
        if (version.loaders != null && !version.loaders.isEmpty())
            loader = version.loaders.get(0);

        com.maxjubayeryt.modbundle.ui.M3ProgressDialog pDialog = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        pDialog.setTitle("Installing " + mod.title);
        pDialog.setMessage("Downloading…");
        pDialog.setProgressStyle(com.maxjubayeryt.modbundle.ui.M3ProgressDialog.STYLE_HORIZONTAL);
        pDialog.setMax(100);
        pDialog.setCancelable(false);
        pDialog.show();

        ModDownloader.DownloadCallback callback = new ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {
                handler.post(() -> { pDialog.setMessage(fileName); pDialog.setProgress(percent); });
            }
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    pDialog.dismiss();
                    Toast.makeText(ModDetailActivity.this, mod.title + " installed!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    pDialog.dismiss();
                    Toast.makeText(ModDetailActivity.this, "Install failed: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };

        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, subFolder,
                dependencies, gameVersion, loader, callback);
        } else {
            java.io.File instanceDir = prefs.getInstanceUri() != null
                ? new java.io.File(prefs.getInstanceUri().getPath()) : null;
            if (instanceDir == null) { pDialog.dismiss(); return; }
            java.io.File targetDir = new java.io.File(instanceDir, subFolder);
            if (!targetDir.exists()) targetDir.mkdirs();
            downloader.downloadMod(file, targetDir, dependencies, gameVersion, loader, callback);
        }
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000f);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000f);
        return String.valueOf(n);
    }
}
