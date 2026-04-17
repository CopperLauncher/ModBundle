package com.modbundle.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.modbundle.app.api.CurseForgeApi;
import com.modbundle.app.api.ModrinthApi;
import com.modbundle.app.model.ModResult;
import com.modbundle.app.model.ModVersion;
import com.modbundle.app.ui.VersionAdapter;
import com.modbundle.app.utils.ModDownloader;
import com.modbundle.app.utils.PrefManager;
import android.app.ProgressDialog;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mod_detail);

        downloader = new ModDownloader(this);
        prefs = new PrefManager(this);

        String modJson = getIntent().getStringExtra(EXTRA_MOD);
        projectType = getIntent().getStringExtra(EXTRA_PROJECT_TYPE);
        source = getIntent().getStringExtra(EXTRA_SOURCE);
        String gameVersion = getIntent().getStringExtra("game_version") != null ? getIntent().getStringExtra("game_version") : "";
        String loader = getIntent().getStringExtra("loader") != null ? getIntent().getStringExtra("loader") : "";
        boolean includeSnapshots = getIntent().getBooleanExtra("include_snapshots", false);
        
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
            if (mod.iconUrl != null && !mod.iconUrl.isEmpty()) {
                Glide.with(this).load(mod.iconUrl).placeholder(R.drawable.ic_mod_default).into(icon);
            } else {
                icon.setImageResource(R.drawable.ic_mod_default);
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
                chip.setTextColor(0xFF9649b8);
                chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFF9649b8));
                chip.setChipStrokeWidth(1f);
                chip.setClickable(false);
                chipGroup.addView(chip);
            }
        }

        api.getProject(mod.projectId, new com.modbundle.app.api.ModrinthApi.Callback<com.modbundle.app.model.ModResult>() {
            public void onSuccess(com.modbundle.app.model.ModResult fullMod) {
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
                cfApi.getLatestFile(mod.projectId, "", "", fileObj -> {
                    handler.post(() -> {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (fileObj == null || !fileObj.has("id") || !fileObj.has("fileName")) {
                            Toast.makeText(this, "No versions found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String fileId = fileObj.get("id").getAsString();
                        String fileName = fileObj.get("fileName").getAsString();
                        if (fileId == null || fileId.isEmpty() || fileName == null || fileName.isEmpty()) {
                            Toast.makeText(this, "No versions found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        cfApi.getDownloadUrl(mod.projectId, fileId, url -> {
                            handler.post(() -> {
                                if (url == null || url.isEmpty()) {
                                    Toast.makeText(this, "Unable to download file", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                ModVersion fakeVersion = new ModVersion();
                                fakeVersion.versionNumber = fileName;
                                fakeVersion.versionType = "release";
                                fakeVersion.dependencies = new java.util.ArrayList<>();
                                ModVersion.VersionFile file = new ModVersion.VersionFile();
                                file.url = url;
                                file.filename = fileName;
                                file.primary = true;
                                fakeVersion.files = java.util.Arrays.asList(file);
                                VersionAdapter adapter = new VersionAdapter(
                                    java.util.Arrays.asList(fakeVersion),
                                    (version, f) -> startDownload(version, f));
                                versionsRecycler.setAdapter(adapter);
                            });
                        }, err -> handler.post(() ->
                            Toast.makeText(this, "CF Error: " + err, Toast.LENGTH_SHORT).show()));
                    });
                }, error -> handler.post(() -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show();
                }));
            } else {
                api.getVersions(mod.projectId, gameVersion, loader, versions -> {
                    handler.post(() -> {
                        if (progress != null) progress.setVisibility(View.GONE);
                        if (versions == null || versions.isEmpty()) {
                            Toast.makeText(this, "No compatible versions found", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        List<ModVersion> filtered = new java.util.ArrayList<>();
                        for (ModVersion v : versions) {
                            String vType = v.versionType != null ? v.versionType : "release";
                            if ("release".equals(vType) || includeSnapshots) filtered.add(v);
                        }
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

    private void startDownload(ModVersion version, ModVersion.VersionFile file) {
        String subFolder = "resourcepack".equals(projectType) ? "resourcepacks"
                         : "shader".equals(projectType) ? "shaderpacks" : "mods";

        ProgressDialog pDialog = new ProgressDialog(this);
        pDialog.setTitle("Installing " + mod.title);
        pDialog.setMessage("Downloading\u2026");
        pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
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
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Boolean> checked = new java.util.ArrayList<>();
        for (ModVersion.Dependency dep : version.dependencies) {
            if (dep == null || dep.projectId == null) continue;
            deps.add(dep);
            String type = dep.dependencyType != null ? dep.dependencyType : "required";
            labels.add(("required".equals(type) ? "Required: " : "Optional: ") + dep.projectId);
            checked.add("required".equals(type));
        }

        if (deps.isEmpty()) {
            startDownload(version, file, version.dependencies);
            return;
        }

        CharSequence[] items = labels.toArray(new CharSequence[0]);
        boolean[] selected = new boolean[checked.size()];
        for (int i = 0; i < checked.size(); i++) selected[i] = checked.get(i);

        new AlertDialog.Builder(this)
            .setTitle("Install dependencies")
            .setMessage("Select which dependencies to install with this mod.")
            .setMultiChoiceItems(items, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
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

        ProgressDialog pDialog = new ProgressDialog(this);
        pDialog.setTitle("Installing " + mod.title);
        pDialog.setMessage("Downloading…");
        pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
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
                dependencies, "", "", callback);
        } else {
            java.io.File instanceDir = prefs.getInstanceUri() != null
                ? new java.io.File(prefs.getInstanceUri().getPath()) : null;
            if (instanceDir == null) { pDialog.dismiss(); return; }
            java.io.File targetDir = new java.io.File(instanceDir, subFolder);
            if (!targetDir.exists()) targetDir.mkdirs();
            downloader.downloadMod(file, targetDir, dependencies, "", "", callback);
        }
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000f);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000f);
        return String.valueOf(n);
    }
}
