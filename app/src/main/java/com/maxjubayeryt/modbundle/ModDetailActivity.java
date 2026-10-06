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
import android.webkit.WebView;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.maxjubayeryt.modbundle.ui.GalleryAdapter;
import com.maxjubayeryt.modbundle.utils.ContentHtml;

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
                modrinthProject = fullMod;
                handler.post(() -> {
                    if (tvFollowers != null) tvFollowers.setText(formatNumber(fullMod.followers));
                    if (tvDownloads != null) tvDownloads.setText(formatNumber(fullMod.downloads));
                });
            }
            public void onError(String error) {}
        });

        setupTabsAndShare();

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


    // ---- Tabs (Versions / Description / Gallery / Changelog) and share --------------------

    private ModResult modrinthProject;
    private boolean descLoaded, galleryLoaded, changelogLoaded;
    private View paneVersions, paneDescription, paneGallery, paneChangelog;

    private boolean isCurse() { return "curseforge".equals(source); }

    private void setupTabsAndShare() {
        ImageButton btnShare = findViewById(R.id.btn_share);
        if (btnShare != null) btnShare.setOnClickListener(v -> shareContent());

        paneVersions = findViewById(R.id.pane_versions);
        paneDescription = findViewById(R.id.pane_description);
        paneGallery = findViewById(R.id.pane_gallery);
        paneChangelog = findViewById(R.id.pane_changelog);

        ContentHtml.setup((WebView) findViewById(R.id.detail_desc_web), this);
        ContentHtml.setup((WebView) findViewById(R.id.detail_changelog_web), this);
        RecyclerView gallery = findViewById(R.id.detail_gallery_recycler);
        gallery.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));

        TabLayout tabs = findViewById(R.id.detail_tabs);
        tabs.addTab(tabs.newTab().setText("Versions"), true); // selected by default
        tabs.addTab(tabs.newTab().setText("Description"));
        tabs.addTab(tabs.newTab().setText("Gallery"));
        tabs.addTab(tabs.newTab().setText("Changelog"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPane(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        showPane(0);
    }

    private void showPane(int index) {
        paneVersions.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        paneDescription.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        paneGallery.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        paneChangelog.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        if (index == 1) loadDescription();
        else if (index == 2) loadGallery();
        else if (index == 3) loadChangelog();
    }

    private void shareContent() {
        String url = contentUrl();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, mod.title);
        send.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(send, "Share"));
    }

    private String contentUrl() {
        if (isCurse()) {
            if (mod.pageUrl != null && !mod.pageUrl.isEmpty()) return mod.pageUrl;
            return "https://www.curseforge.com/minecraft/search?search=" + Uri.encode(mod.title != null ? mod.title : "");
        }
        String type = "resourcepack".equals(projectType) ? "resourcepack"
                    : "shader".equals(projectType) ? "shader" : "mod";
        String id = mod.slug != null && !mod.slug.isEmpty() ? mod.slug : mod.projectId;
        return "https://modrinth.com/" + type + "/" + id;
    }

    /** Full Modrinth project (has the long body and gallery, which search results lack). Callback runs off the main thread. */
    private void withModrinthProject(ModrinthApi.Callback<ModResult> cb) {
        if (modrinthProject != null && modrinthProject.body != null) { cb.onSuccess(modrinthProject); return; }
        api.getProject(mod.projectId, new ModrinthApi.Callback<ModResult>() {
            public void onSuccess(ModResult project) { modrinthProject = project; cb.onSuccess(project); }
            public void onError(String error) { cb.onError(error); }
        });
    }

    private void showPaneHtml(int progressId, int webId, int emptyId, String html, String emptyText, String baseUrl) {
        View progress = findViewById(progressId);
        WebView web = findViewById(webId);
        TextView empty = findViewById(emptyId);
        progress.setVisibility(View.GONE);
        if (html == null || html.trim().isEmpty()) {
            web.setVisibility(View.GONE);
            empty.setText(emptyText);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
            ContentHtml.show(web, web, html, baseUrl);
        }
    }

    private void loadDescription() {
        if (descLoaded) return;
        descLoaded = true;
        findViewById(R.id.detail_desc_progress).setVisibility(View.VISIBLE);
        final String base = isCurse() ? "https://www.curseforge.com/" : "https://modrinth.com/";
        if (isCurse()) {
            cfApi.getDescription(mod.projectId,
                html -> handler.post(() -> showPaneHtml(R.id.detail_desc_progress, R.id.detail_desc_web, R.id.detail_desc_empty, html, "No description available.", base)),
                err -> handler.post(() -> { descLoaded = false; showPaneHtml(R.id.detail_desc_progress, R.id.detail_desc_web, R.id.detail_desc_empty, null, "Couldn't load the description. Switch tabs to retry.", base); }));
        } else {
            withModrinthProject(new ModrinthApi.Callback<ModResult>() {
                public void onSuccess(ModResult project) {
                    String md = project.body != null && !project.body.trim().isEmpty() ? project.body : mod.description;
                    String html = ContentHtml.markdownToHtml(md);
                    handler.post(() -> showPaneHtml(R.id.detail_desc_progress, R.id.detail_desc_web, R.id.detail_desc_empty, html, "No description available.", base));
                }
                public void onError(String error) {
                    handler.post(() -> { descLoaded = false; showPaneHtml(R.id.detail_desc_progress, R.id.detail_desc_web, R.id.detail_desc_empty, null, "Couldn't load the description. Switch tabs to retry.", base); });
                }
            });
        }
    }

    private void loadGallery() {
        if (galleryLoaded) return;
        galleryLoaded = true;
        findViewById(R.id.detail_gallery_progress).setVisibility(View.VISIBLE);
        if (isCurse()) {
            int id;
            try { id = Integer.parseInt(mod.projectId); } catch (NumberFormatException e) { showGallery(null, false); return; }
            cfApi.getMod(id, data -> {
                List<ModResult.GalleryImage> list = new java.util.ArrayList<>();
                if (data.has("screenshots") && data.get("screenshots").isJsonArray()) {
                    JsonArray arr = data.getAsJsonArray("screenshots");
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject o = arr.get(i).getAsJsonObject();
                        ModResult.GalleryImage g = new ModResult.GalleryImage();
                        g.url = o.has("url") && !o.get("url").isJsonNull() ? o.get("url").getAsString() : null;
                        g.title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : null;
                        g.description = o.has("description") && !o.get("description").isJsonNull() ? o.get("description").getAsString() : null;
                        if (g.url != null) list.add(g);
                    }
                }
                handler.post(() -> showGallery(list, true));
            }, err -> handler.post(() -> showGallery(null, false)));
        } else {
            withModrinthProject(new ModrinthApi.Callback<ModResult>() {
                public void onSuccess(ModResult project) {
                    List<ModResult.GalleryImage> list = project.galleryImages();
                    handler.post(() -> showGallery(list, true));
                }
                public void onError(String error) { handler.post(() -> showGallery(null, false)); }
            });
        }
    }

    private void showGallery(List<ModResult.GalleryImage> images, boolean ok) {
        findViewById(R.id.detail_gallery_progress).setVisibility(View.GONE);
        TextView empty = findViewById(R.id.detail_gallery_empty);
        RecyclerView rv = findViewById(R.id.detail_gallery_recycler);
        if (!ok) galleryLoaded = false; // allow a retry by switching tabs
        if (images == null || images.isEmpty()) {
            rv.setVisibility(View.GONE);
            empty.setText(ok ? "No gallery images." : "Couldn't load the gallery. Switch tabs to retry.");
            empty.setVisibility(View.VISIBLE);
            return;
        }
        empty.setVisibility(View.GONE);
        rv.setVisibility(View.VISIBLE);
        rv.setAdapter(new GalleryAdapter(images, this::showGalleryImage));
    }

    private void showGalleryImage(ModResult.GalleryImage img) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(24, 16, 24, 0);
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        com.bumptech.glide.Glide.with(this).load(img.url).into(iv);
        box.addView(iv);
        if (img.description != null && !img.description.trim().isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(img.description);
            tv.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(tv));
            tv.setTextSize(13f);
            tv.setPadding(0, 16, 0, 0);
            box.addView(tv);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(box);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(img.title != null && !img.title.trim().isEmpty() ? img.title : "Image")
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private static String dateOnly(String iso) {
        return iso != null && iso.length() >= 10 ? iso.substring(0, 10) : (iso != null ? iso : "");
    }

    private void loadChangelog() {
        if (changelogLoaded) return;
        changelogLoaded = true;
        findViewById(R.id.detail_changelog_progress).setVisibility(View.VISIBLE);
        final String base = isCurse() ? "https://www.curseforge.com/" : "https://modrinth.com/";
        final String emptyText = "No changelog available.";
        final String errText = "Couldn't load the changelog. Switch tabs to retry.";
        if (isCurse()) {
            cfApi.getFiles(mod.projectId, "", "", files ->
                cfApi.getChangelogs(mod.projectId, files, 15, entries -> {
                    StringBuilder sb = new StringBuilder();
                    for (String[] e : entries) {
                        sb.append("<div class=\"v\"><h3>").append(ContentHtml.escape(e[0])).append("</h3><div class=\"meta\">")
                          .append(ContentHtml.escape(dateOnly(e[1]))).append("</div>")
                          .append(e[2] == null || e[2].trim().isEmpty() ? "<div class=\"none\">No changelog provided.</div>" : e[2])
                          .append("</div>");
                    }
                    String html = sb.toString();
                    handler.post(() -> showPaneHtml(R.id.detail_changelog_progress, R.id.detail_changelog_web, R.id.detail_changelog_empty, html, emptyText, base));
                }, err -> handler.post(() -> { changelogLoaded = false; showPaneHtml(R.id.detail_changelog_progress, R.id.detail_changelog_web, R.id.detail_changelog_empty, null, errText, base); })),
                err -> handler.post(() -> { changelogLoaded = false; showPaneHtml(R.id.detail_changelog_progress, R.id.detail_changelog_web, R.id.detail_changelog_empty, null, errText, base); }));
        } else {
            // Unfiltered on purpose: the changelog tab shows the project's whole history, not just
            // the versions matching the current instance.
            api.getVersions(mod.projectId, "", "", versions -> {
                StringBuilder sb = new StringBuilder();
                int shown = 0;
                for (ModVersion v : versions) {
                    if (shown++ >= 50) break;
                    String title = v.name != null && !v.name.isEmpty() ? v.name : v.versionNumber;
                    sb.append("<div class=\"v\"><h3>").append(ContentHtml.escape(title)).append("</h3><div class=\"meta\">")
                      .append(ContentHtml.escape(v.versionNumber != null ? v.versionNumber : "")).append(" &middot; ")
                      .append(ContentHtml.escape(dateOnly(v.datePublished))).append("</div>");
                    String body = ContentHtml.markdownToHtml(v.changelog);
                    sb.append(body.isEmpty() ? "<div class=\"none\">No changelog provided.</div>" : body).append("</div>");
                }
                String html = sb.toString();
                handler.post(() -> showPaneHtml(R.id.detail_changelog_progress, R.id.detail_changelog_web, R.id.detail_changelog_empty, html, emptyText, base));
            }, err -> handler.post(() -> { changelogLoaded = false; showPaneHtml(R.id.detail_changelog_progress, R.id.detail_changelog_web, R.id.detail_changelog_empty, null, errText, base); }));
        }
    }

    private String formatNumber(int n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000f);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000f);
        return String.valueOf(n);
    }
}
