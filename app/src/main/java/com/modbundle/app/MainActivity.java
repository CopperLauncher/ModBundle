package com.modbundle.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.os.Environment;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.modbundle.app.api.ModrinthApi;
import com.modbundle.app.api.CurseForgeApi;
import com.modbundle.app.model.ModResult;
import com.modbundle.app.model.ModVersion;
import com.modbundle.app.model.SearchResponse;
import com.modbundle.app.ui.InstalledModsAdapter;
import com.modbundle.app.ui.ModAdapter;
import com.modbundle.app.ui.InstanceAdapter;
import com.modbundle.app.ui.SavedPathsAdapter;
import java.util.ArrayList;
import com.modbundle.app.utils.ModDownloader;
import com.modbundle.app.utils.PrefManager;
import com.modbundle.app.ModDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FOLDER = 3001;

    // Views
    private View layoutBrowse, layoutInstalled, layoutSettings, layoutInstances;
    private EditText searchInput;
    private Spinner spinnerVersion, spinnerLoader;
    private RecyclerView browseRecycler, installedRecycler;
    private TextView emptyBrowse, emptyInstalled, tvFolderPath;
    private ProgressBar browseProgress;
    private Button btnLoadMore, btnChooseFolder;

    // State
    private final List<ModResult> modResults = new ArrayList<>();
    private final List<Object> installedMods = new ArrayList<>();
    private ModAdapter modAdapter;
    private InstalledModsAdapter installedAdapter;

    private final ModrinthApi api = new ModrinthApi();
    private final CurseForgeApi curseForgeApi = new CurseForgeApi();
    private boolean useCurseForge = false;
    private String currentProjectType = "mod";
    private Button btnModrinth, btnCurseForge;
    private Button btnTypeMods, btnTypeResourcepack, btnTypeShader;
    private TextView installedTabMods, installedTabShaders, installedTabResourcepacks, tvInstalledCount;
    private String currentInstalledType = "mods";
    private android.widget.CheckBox cbSelectAll;
    private android.widget.Button btnCheckUpdates, btnUpdateAll, btnUpdateSelected;
    private android.view.View layoutUpdateBar;
    private android.widget.CheckBox btnSnapshots;
    private boolean includeSnapshots = false;
    private RecyclerView instancesRecycler;
    private ImageButton btnScanInstances;
    private InstanceAdapter instanceAdapter;
    private final java.util.List<java.io.File> instanceList = new ArrayList<>();
    private RecyclerView savedPathsRecycler;
    private ModDownloader downloader;
    private com.modbundle.app.utils.InstanceNameStore instanceNameStore;
    private PrefManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int currentOffset = 0;
    private String currentQuery = "";
    private boolean isLoading = false;

    private static final String[] LOADERS = {
        "Any", "fabric", "forge", "neoforge", "quilt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefManager(this);
        downloader = new ModDownloader(this);
        instanceNameStore = new com.modbundle.app.utils.InstanceNameStore(this);
        requestStoragePermissionIfNeeded();
        initViews();
        setupBottomNav();
        setupFilters();
        setupSearch();
        setupBrowseRecycler();
        setupSourceToggle();
        setupTypeToggle();
        setupInstalledRecycler();
        setupSettings();
        requestManageStoragePermission();
        setupSavedPaths();
        setupInstances();

        showTab("browse");

        // Prompt to set folder if not set
        if (!prefs.hasModsFolder()) {
            showFolderPickerPrompt();
        } else {
            updateFolderLabel();
            refreshSavedPaths();
        }
    }

    private void requestStoragePermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, 100);
        }
    }

    private void initViews() {
        layoutBrowse    = findViewById(R.id.layout_browse);
        layoutInstalled = findViewById(R.id.layout_installed);
        layoutSettings  = findViewById(R.id.layout_settings);
        layoutInstances = findViewById(R.id.layout_instances);
        searchInput     = findViewById(R.id.search_input);
        spinnerVersion  = findViewById(R.id.spinner_version);
        spinnerLoader   = findViewById(R.id.spinner_loader);
        browseRecycler  = findViewById(R.id.browse_recycler);
        installedRecycler = findViewById(R.id.installed_recycler);
        emptyBrowse     = findViewById(R.id.empty_browse);
        emptyInstalled  = findViewById(R.id.empty_installed);
        tvFolderPath    = findViewById(R.id.tv_folder_path);
        browseProgress  = findViewById(R.id.browse_progress);
        btnModrinth = findViewById(R.id.btn_modrinth);
        btnCurseForge = findViewById(R.id.btn_curseforge);
        btnTypeMods = findViewById(R.id.btn_type_mods);
        btnTypeResourcepack = findViewById(R.id.btn_type_resourcepack);
        btnTypeShader = findViewById(R.id.btn_type_shader);
        btnLoadMore     = findViewById(R.id.btn_load_more);
        btnSnapshots    = findViewById(R.id.btn_snapshots);
        btnChooseFolder = findViewById(R.id.btn_choose_folder);
        installedTabMods = findViewById(R.id.installed_tab_mods);
        installedTabShaders = findViewById(R.id.installed_tab_shaders);
        installedTabResourcepacks = findViewById(R.id.installed_tab_resourcepacks);
        tvInstalledCount = findViewById(R.id.tv_installed_count);
        cbSelectAll = findViewById(R.id.cb_select_all);
        btnCheckUpdates = findViewById(R.id.btn_check_updates);
        btnUpdateAll = findViewById(R.id.btn_update_all);
        btnUpdateSelected = findViewById(R.id.btn_update_selected);
        layoutUpdateBar = findViewById(R.id.layout_update_bar);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_browse) {
                showTab("browse"); return true;
            } else if (id == R.id.nav_installed) {
                showTab("installed"); refreshInstalled(); return true;
            } else if (id == R.id.nav_instances) {
                showTab("instances"); return true;
            } else if (id == R.id.nav_settings) {
                showTab("settings"); return true;
            }
            return false;
        });
    }

    private void showTab(String tab) {
        layoutBrowse.setVisibility("browse".equals(tab) ? View.VISIBLE : View.GONE);
        layoutInstalled.setVisibility("installed".equals(tab) ? View.VISIBLE : View.GONE);
        layoutSettings.setVisibility("settings".equals(tab) ? View.VISIBLE : View.GONE);
        if (layoutInstances != null) layoutInstances.setVisibility("instances".equals(tab) ? View.VISIBLE : View.GONE);
    }

    private void setupFilters() {
        api.getGameVersions(includeSnapshots, versions -> {
            String[] versionArray = versions.toArray(new String[0]);
            runOnUiThread(() -> {
                ArrayAdapter<String> vAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, versionArray);
                vAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerVersion.setAdapter(vAdapter);
                String savedVer = prefs.getGameVersion();
                if (!savedVer.isEmpty()) {
                    int idx = versions.indexOf(savedVer);
                    if (idx >= 0) spinnerVersion.setSelection(idx);
                }
                if (prefs.hasModsFolder()) {
                    searchMods(true);
                }
            });
        }, error -> runOnUiThread(() ->
            Toast.makeText(this, "Failed to load versions", Toast.LENGTH_SHORT).show()
        ));

        ArrayAdapter<String> lAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, LOADERS);
        lAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLoader.setAdapter(lAdapter);

        String savedLoader = prefs.getLoader();
        if (!savedLoader.isEmpty()) {
            int idx = Arrays.asList(LOADERS).indexOf(savedLoader);
            if (idx >= 0) spinnerLoader.setSelection(idx);
        }

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            boolean ready = false;
            public void onItemSelected(AdapterView<?> a, View v, int p, long id) {
                if (!ready) { ready = true; return; }
                saveFilters();
                searchMods(true);
            }
            public void onNothingSelected(AdapterView<?> a) {}
        };
        spinnerVersion.setOnItemSelectedListener(filterListener);
        spinnerLoader.setOnItemSelectedListener(filterListener);

        btnLoadMore.setOnClickListener(v -> searchMods(false));
        btnSnapshots.setOnCheckedChangeListener((b, checked) -> {
            includeSnapshots = checked;
            api.getGameVersions(includeSnapshots, versions -> {
                String[] arr = versions.toArray(new String[0]);
                runOnUiThread(() -> {
                    android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item, arr);
                    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerVersion.setAdapter(a);
                });
            }, e -> {});
        });
    }

    private void setupTypeToggle() {
        android.content.res.ColorStateList active = android.content.res.ColorStateList.valueOf(0xFF9649b8);
        android.content.res.ColorStateList inactive = android.content.res.ColorStateList.valueOf(0xFF242424);
        btnTypeMods.setOnClickListener(v -> {
            currentProjectType = "mod";
            btnTypeMods.setBackgroundTintList(active); btnTypeMods.setTextColor(0xFFFFFFFF);
            btnTypeResourcepack.setBackgroundTintList(inactive); btnTypeResourcepack.setTextColor(0xFFAAAAAA);
            btnTypeShader.setBackgroundTintList(inactive); btnTypeShader.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnTypeResourcepack.setOnClickListener(v -> {
            currentProjectType = "resourcepack";
            btnTypeResourcepack.setBackgroundTintList(active); btnTypeResourcepack.setTextColor(0xFFFFFFFF);
            btnTypeMods.setBackgroundTintList(inactive); btnTypeMods.setTextColor(0xFFAAAAAA);
            btnTypeShader.setBackgroundTintList(inactive); btnTypeShader.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnTypeShader.setOnClickListener(v -> {
            currentProjectType = "shader";
            btnTypeShader.setBackgroundTintList(active); btnTypeShader.setTextColor(0xFFFFFFFF);
            btnTypeMods.setBackgroundTintList(inactive); btnTypeMods.setTextColor(0xFFAAAAAA);
            btnTypeResourcepack.setBackgroundTintList(inactive); btnTypeResourcepack.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
    }

    private void setupSavedPaths() {
        refreshSavedPaths();
    }

    private void refreshSavedPaths() {
        java.util.List<android.net.Uri> saved = prefs.getSavedPaths();
        android.net.Uri active = prefs.getModsUri();
        if (savedPathsRecycler == null && layoutInstances != null)
            savedPathsRecycler = layoutInstances.findViewById(R.id.saved_paths_recycler);
        if (savedPathsRecycler == null) return;
        if (saved.isEmpty()) {
            savedPathsRecycler.setVisibility(View.GONE);
            return;
        }
        savedPathsRecycler.setVisibility(View.VISIBLE);
        savedPathsRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        savedPathsRecycler.setAdapter(new SavedPathsAdapter(this, saved, active, new SavedPathsAdapter.Listener() {
            @Override public void onUse(android.net.Uri uri) {
                prefs.saveModsUri(uri);
                updateFolderLabel();
                refreshSavedPaths();
                Toast.makeText(MainActivity.this, "Switched to saved path", Toast.LENGTH_SHORT).show();
            }
            @Override public void onRemove(android.net.Uri uri) {
                prefs.removeSavedPath(uri);
                updateFolderLabel();
                refreshSavedPaths();
            }
        }));
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void setupInstances() {
        instanceAdapter = new InstanceAdapter(this, instanceList, (instanceFolder, name) -> {
            android.net.Uri uri = android.net.Uri.fromFile(instanceFolder);
            prefs.saveInstanceUri(uri);
            updateFolderLabel();
            updateActiveInstanceLabel();
            Toast.makeText(this, "Instance set: " + name, Toast.LENGTH_SHORT).show();
        });

        // Wire rename listener
        instanceAdapter.setRenameListener((instance, currentName) -> {
            android.widget.EditText input = new android.widget.EditText(this);
            input.setText(currentName);
            input.setTextColor(0xFFFFFFFF);
            input.setSelectAllOnFocus(true);
            input.setPadding(48, 24, 48, 8);
            new AlertDialog.Builder(this)
                .setTitle("Rename Instance")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        instanceNameStore.setName(instance.getAbsolutePath(), newName);
                        instanceAdapter.notifyDataSetChanged();
                        updateActiveInstanceLabel();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Wire logo picker listener
        instanceAdapter.setLogoListener((instance, path) -> {
            String[] logoNames = {
                "ic_launcher_monochrome", "ic_fabric", "ic_quilt",
                "ic_pojav_full", "ic_curseforge", "ic_modrinth",
                "ic_mg_renderer", "ic_menu_home", "ic_folder",
                "ic_add_modded", "ic_menu_settings", "ic_file"
            };
            String[] labels = {
                "Default", "Fabric", "Quilt",
                "PojavLauncher", "CurseForge", "Modrinth",
                "MG Renderer", "Home", "Folder",
                "Modded", "Settings", "File"
            };
            new AlertDialog.Builder(this)
                .setTitle("Choose Logo")
                .setItems(labels, (d, which) -> {
                    instanceNameStore.setLogo(instance.getAbsolutePath(), logoNames[which]);
                    instanceAdapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        android.view.View instLayout = layoutInstances;
        if (instLayout != null) {
            instancesRecycler = instLayout.findViewById(R.id.instances_recycler);
            savedPathsRecycler = null; // removed from instances tab
            btnScanInstances = instLayout.findViewById(R.id.btn_scan_instances);
            ImageButton btnChooseFromInst = instLayout.findViewById(R.id.btn_choose_folder);
            ImageButton btnAddInstance = instLayout.findViewById(R.id.btn_add_instance);

            if (instancesRecycler != null) {
                instancesRecycler.setLayoutManager(new LinearLayoutManager(this));
                instancesRecycler.setAdapter(instanceAdapter);
            }
            if (btnScanInstances != null) btnScanInstances.setOnClickListener(v -> scanForInstances());
            if (btnChooseFromInst != null) btnChooseFromInst.setOnClickListener(v -> openFolderPicker());
            if (btnAddInstance != null) btnAddInstance.setOnClickListener(v -> openFolderPicker());
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            scanForInstances();
        }
        updateActiveInstanceLabel();
    }

    private void updateActiveInstanceLabel() {
        if (layoutInstances == null) return;
        android.widget.TextView tvActive = layoutInstances.findViewById(R.id.tv_active_instance);
        if (tvActive == null) return;
        android.net.Uri uri = prefs.getInstanceUri();
        if (uri == null) {
            tvActive.setText("No instance selected");
        } else {
            String path = "file".equals(uri.getScheme()) ? uri.getPath() : uri.toString();
            String customName = instanceNameStore.getName(path);
            String display = (customName != null && !customName.isEmpty()) ? customName : uri.getLastPathSegment();
            if (display == null) display = uri.toString();
            tvActive.setText("Active: " + display);
        }
    }

    private void scanForInstances() {
        instanceList.clear();
        String ext = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String[] basePaths = {
            ext + "/games/PojavLauncher/custom_instances",
            ext + "/games/CopperLauncher/custom_instances",
            ext + "/games/Amethyst/custom_instances",
            ext + "/games/PojavLauncher/instances",
        };
        for (String path : basePaths) {
            java.io.File dir = new java.io.File(path);
            if (dir.exists() && dir.isDirectory()) {
                java.io.File[] instances = dir.listFiles();
                if (instances != null) {
                    for (java.io.File f : instances) {
                        if (f.isDirectory()) instanceList.add(f);
                    }
                }
            }
        }
        instanceAdapter.notifyDataSetChanged();
        if (instanceList.isEmpty()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("No instances found")
                    .setMessage("On Android 11+, launcher files in Android/data/ can't be accessed automatically.\n\nTap 'Browse' to manually navigate to your launcher's custom_instances folder.")
                    .setPositiveButton("Browse Android/data", (d, w) -> {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.putExtra("android.provider.extra.INITIAL_URI",
                            android.provider.DocumentsContract.buildDocumentUri(
                                "com.android.externalstorage.documents", "primary:Android/data"));
                        startActivityForResult(intent, REQUEST_FOLDER);
                    })
                    .setNegativeButton("Use Manual Picker", (d, w) -> btnChooseFolder.performClick())
                    .show();
            } else {
                Toast.makeText(this, "No instances found. Choose folder manually.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupSourceToggle() {
        btnModrinth.setOnClickListener(v -> {
            useCurseForge = false;
            btnModrinth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9649b8));
            btnModrinth.setTextColor(0xFFFFFFFF);
            btnCurseForge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF242424));
            btnCurseForge.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
        btnCurseForge.setOnClickListener(v -> {
            useCurseForge = true;
            btnCurseForge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF9649b8));
            btnCurseForge.setTextColor(0xFFFFFFFF);
            btnModrinth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF242424));
            btnModrinth.setTextColor(0xFFAAAAAA);
            searchMods(true);
        });
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            private final Handler h = new Handler(Looper.getMainLooper());
            private Runnable pending;
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (pending != null) h.removeCallbacks(pending);
                pending = () -> searchMods(true);
                h.postDelayed(pending, 500);
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBrowseRecycler() {
        modAdapter = new ModAdapter(this, modResults, new com.modbundle.app.ui.ModAdapter.OnInstallClickListener() {
            public void onInstallClick(com.modbundle.app.model.ModResult mod) {
                if (!prefs.hasInstanceFolder()) { showFolderPickerPrompt(); return; }
                showInstallDialog(mod);
            }
            public void onModClick(com.modbundle.app.model.ModResult mod) {
                if (!prefs.hasInstanceFolder()) { showFolderPickerPrompt(); return; }
                String modJson = new com.google.gson.Gson().toJson(mod);
                Intent intent = new Intent(MainActivity.this, ModDetailActivity.class);
                intent.putExtra(ModDetailActivity.EXTRA_MOD, modJson);
                intent.putExtra(ModDetailActivity.EXTRA_PROJECT_TYPE, currentProjectType);
                intent.putExtra(ModDetailActivity.EXTRA_SOURCE, mod.source);
                intent.putExtra("game_version", getSelectedVersion());
                intent.putExtra("loader", getSelectedLoader());
                intent.putExtra("include_snapshots", includeSnapshots);
                startActivity(intent);
            }
        });
        browseRecycler.setLayoutManager(new LinearLayoutManager(this));
        browseRecycler.setAdapter(modAdapter);
    }

    private void setupInstalledRecycler() {
        installedTabMods.setOnClickListener(v -> { currentInstalledType = "mods"; switchInstalledTab(); refreshInstalled(); });
        installedTabShaders.setOnClickListener(v -> { currentInstalledType = "shaderpacks"; switchInstalledTab(); refreshInstalled(); });
        installedTabResourcepacks.setOnClickListener(v -> { currentInstalledType = "resourcepacks"; switchInstalledTab(); refreshInstalled(); });

        installedAdapter = new InstalledModsAdapter(installedMods,
            mod -> {
                String modName = (mod instanceof androidx.documentfile.provider.DocumentFile)
                    ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                    : ((java.io.File) mod).getName();
                new AlertDialog.Builder(this)
                    .setTitle("Delete?")
                    .setMessage("Remove \"" + modName + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        boolean deleted = (mod instanceof androidx.documentfile.provider.DocumentFile)
                            ? ((androidx.documentfile.provider.DocumentFile) mod).delete()
                            : ((java.io.File) mod).delete();
                        if (deleted) { refreshInstalled(); Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show(); }
                    })
                    .setNegativeButton("Cancel", null).show();
            },
            mod -> {
                if (!"mods".equals(currentInstalledType)) return;
                if (mod instanceof androidx.documentfile.provider.DocumentFile) {
                    androidx.documentfile.provider.DocumentFile df = (androidx.documentfile.provider.DocumentFile) mod;
                    String name = df.getName(); if (name == null) return;
                    df.renameTo(name.endsWith(".disabled") ? name.replace(".disabled", "") : name + ".disabled");
                    refreshInstalled();
                } else if (mod instanceof java.io.File) {
                    java.io.File f = (java.io.File) mod;
                    String name = f.getName();
                    f.renameTo(new java.io.File(f.getParent(), name.endsWith(".disabled") ? name.replace(".disabled", "") : name + ".disabled"));
                    refreshInstalled();
                }
            },
            (mod, meta) -> performUpdate(mod, meta)
        );

        btnCheckUpdates.setOnClickListener(v -> checkUpdates());

        btnUpdateSelected.setOnClickListener(v -> {
            java.util.List<Object> toUpdate = installedAdapter.getSelectedMods();
            if (toUpdate.isEmpty()) {
                Toast.makeText(this, "No mods selected", Toast.LENGTH_SHORT).show();
                return;
            }
            for (Object mod : toUpdate) {
                String name = (mod instanceof androidx.documentfile.provider.DocumentFile)
                    ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                    : ((java.io.File) mod).getName();
                com.modbundle.app.utils.ModMetadata meta = installedAdapter.getMetaCache().get(name);
                if (meta != null && meta.hasUpdate) performUpdate(mod, meta);
            }
        });

        btnUpdateAll.setOnClickListener(v -> {
            java.util.List<Object> toUpdate = installedAdapter.getSelectedMods();
            if (toUpdate.isEmpty()) {
                for (Object mod : installedMods) {
                    String name = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                        : ((java.io.File) mod).getName();
                    com.modbundle.app.utils.ModMetadata meta = installedAdapter.getMetaCache().get(name);
                    if (meta != null && meta.hasUpdate) performUpdate(mod, meta);
                }
            } else {
                for (Object mod : toUpdate) {
                    String name = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                        : ((java.io.File) mod).getName();
                    com.modbundle.app.utils.ModMetadata meta = installedAdapter.getMetaCache().get(name);
                    if (meta != null && meta.hasUpdate) performUpdate(mod, meta);
                }
            }
        });

        cbSelectAll.setOnCheckedChangeListener((btn, checked) -> {
            installedAdapter.setShowCheckboxes(true);
            if (checked) installedAdapter.selectAll();
            else installedAdapter.deselectAll();
        });
        installedRecycler.setLayoutManager(new LinearLayoutManager(this));
        installedRecycler.setAdapter(installedAdapter);
    }

    private void switchInstalledTab() {
        installedTabMods.setTextColor("mods".equals(currentInstalledType) ? 0xFF9649b8 : 0xFF888888);
        installedTabMods.setTypeface(null, "mods".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedTabShaders.setTextColor("shaderpacks".equals(currentInstalledType) ? 0xFF9649b8 : 0xFF888888);
        installedTabShaders.setTypeface(null, "shaderpacks".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedTabResourcepacks.setTextColor("resourcepacks".equals(currentInstalledType) ? 0xFF9649b8 : 0xFF888888);
        installedTabResourcepacks.setTypeface(null, "resourcepacks".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedAdapter.setShowDisable("mods".equals(currentInstalledType));
        installedAdapter.setCurrentType(currentInstalledType);
        installedAdapter.notifyDataSetChanged();
    }

    private void setupSettings() {
        btnChooseFolder.setOnClickListener(v -> openFolderPicker());
        updateFolderLabel();
    }

    private void searchMods(boolean reset) {
        if (isLoading) return;
        if (reset) {
            currentOffset = 0;
            modResults.clear();
            modAdapter.notifyDataSetChanged();
        }

        isLoading = true;
        browseProgress.setVisibility(View.VISIBLE);
        btnLoadMore.setVisibility(View.GONE);
        emptyBrowse.setVisibility(View.GONE);

        currentQuery = searchInput.getText().toString().trim();
        String version = getSelectedVersion();
        String loader  = getSelectedLoader();

        if (useCurseForge) {
            curseForgeApi.searchMods(currentQuery, version, loader, currentOffset, currentProjectType, results -> {
                runOnUiThread(() -> {
                    browseProgress.setVisibility(android.view.View.GONE);
                    isLoading = false;
                    if (reset) { modAdapter.getMods().clear(); modAdapter.notifyDataSetChanged(); }
                    if (results.isEmpty()) {
                        if (modAdapter.getItemCount() == 0) emptyBrowse.setVisibility(android.view.View.VISIBLE);
                        btnLoadMore.setVisibility(android.view.View.GONE);
                    } else {
                        emptyBrowse.setVisibility(android.view.View.GONE);
                        modAdapter.getMods().addAll(results); modAdapter.notifyDataSetChanged();
                        currentOffset += results.size();
                        btnLoadMore.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }, error -> runOnUiThread(() -> {
                browseProgress.setVisibility(android.view.View.GONE);
                isLoading = false;
                Toast.makeText(this, "CurseForge error: " + error, Toast.LENGTH_SHORT).show();
            }));
            return;
        }
        api.searchMods(currentQuery, version, loader, currentOffset, currentProjectType, new ModrinthApi.Callback<SearchResponse>() {
            public void onSuccess(SearchResponse result) {
                handler.post(() -> {
                    isLoading = false;
                    browseProgress.setVisibility(View.GONE);
                    if (result.hits != null) {
                        for (ModResult mod : result.hits) {
                            mod.isInstalled = false;
                        }
                        modResults.addAll(result.hits);
                        modAdapter.notifyDataSetChanged();
                        currentOffset += result.hits.size();
                        btnLoadMore.setVisibility(
                            currentOffset < result.totalHits ? View.VISIBLE : View.GONE);
                    }
                    emptyBrowse.setVisibility(modResults.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
            public void onError(String error) {
                handler.post(() -> {
                    isLoading = false;
                    browseProgress.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    emptyBrowse.setVisibility(modResults.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
        });
    }

    private void showInstallDialog(ModResult mod) {
        String version = getSelectedVersion();
        String loader  = getSelectedLoader();
        ProgressDialog loading = new ProgressDialog(this);
        loading.setMessage("Fetching versions…");
        loading.show();

        if ("curseforge".equals(mod.source)) {
            curseForgeApi.getLatestFile(mod.projectId, version, loader, fileObj -> {
                handler.post(() -> {
                    loading.dismiss();
                    String fileId = fileObj.get("id").getAsString();
                    String fileName = fileObj.get("fileName").getAsString();
                    curseForgeApi.getDownloadUrl(mod.projectId, fileId, url -> {
                        handler.post(() -> {
                            new AlertDialog.Builder(this)
                                .setTitle("Install: " + mod.title)
                                .setMessage(fileName)
                                .setPositiveButton("Install", (d, w) -> {
                                    ModVersion.VersionFile file = new ModVersion.VersionFile();
                                    file.url = url;
                                    file.filename = fileName;
                                    ModVersion fakeVersion = new ModVersion();
                                    fakeVersion.versionNumber = fileName;
                                    fakeVersion.dependencies = new java.util.ArrayList<>();
                                    startDownload(mod, fakeVersion, file);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        });
                    }, error2 -> handler.post(() ->
                        Toast.makeText(this, "CF Error: " + error2, Toast.LENGTH_SHORT).show()
                    ));
                });
            }, error -> handler.post(() -> {
                loading.dismiss();
                Toast.makeText(this, "CF Error: " + error, Toast.LENGTH_SHORT).show();
            }));
            return;
        }
        api.getVersions(mod.projectId, version, loader, versions -> {
            handler.post(() -> {
                loading.dismiss();
                if (versions == null || versions.isEmpty()) {
                    Toast.makeText(this, "No compatible versions found.", Toast.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[versions.size()];
                for (int i = 0; i < versions.size(); i++) {
                    ModVersion v = versions.get(i);
                    labels[i] = v.versionNumber + " (" + String.join(", ", v.gameVersions) + ")";
                }
                new AlertDialog.Builder(this)
                    .setTitle("Install: " + mod.title)
                    .setItems(labels, (d, which) -> {
                        ModVersion selected = versions.get(which);
                        ModVersion.VersionFile file = ModDownloader.getPrimaryFile(selected);
                        if (file != null) startDownload(mod, selected, file);
                    })
                    .setNegativeButton("Cancel", null).show();
            });
        }, error -> handler.post(() -> { loading.dismiss(); Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show(); }));
    }

    private void startDownload(ModResult mod, ModVersion version, ModVersion.VersionFile file) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Installing " + mod.title);
        progress.setMessage("Downloading…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        ModDownloader.DownloadCallback callback = new ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {
                handler.post(() -> { progress.setMessage(fileName); progress.setProgress(percent); });
            }
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(MainActivity.this, mod.title + " installed!", Toast.LENGTH_SHORT).show();
                    mod.isInstalled = true;
                    modAdapter.notifyDataSetChanged();
                });
            }
            public void onError(String error) {
                handler.post(() -> { progress.dismiss(); Toast.makeText(MainActivity.this, "Install failed: " + error, Toast.LENGTH_LONG).show(); });
            }
        };

        String subFolder = "resourcepack".equals(currentProjectType) ? "resourcepacks" : "shader".equals(currentProjectType) ? "shaderpacks" : "mods";
        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, subFolder, version.dependencies, getSelectedVersion(), getSelectedLoader(), callback);
        } else {
            java.io.File targetDir = getTargetDirLegacy();
            if (targetDir == null) { progress.dismiss(); showFolderPickerPrompt(); return; }
            downloader.downloadMod(file, targetDir, version.dependencies, getSelectedVersion(), getSelectedLoader(), callback);
        }
    }

    private void checkUpdates() {
        if (!"mods".equals(currentInstalledType)) return;
        btnCheckUpdates.setEnabled(false);
        btnCheckUpdates.setText("Checking...");
        installedAdapter.getMetaCache().clear();
        installedAdapter.notifyDataSetChanged();

        java.util.List<Object> modsCopy = new java.util.ArrayList<>(installedMods);
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(modsCopy.size());
        java.util.concurrent.atomic.AtomicInteger updatesFound = new java.util.concurrent.atomic.AtomicInteger(0);

        for (Object mod : modsCopy) {
            new Thread(() -> {
                try {
                    com.modbundle.app.utils.ModMetadata meta = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? com.modbundle.app.utils.ModMetadataParser.parse(this, (androidx.documentfile.provider.DocumentFile) mod)
                        : com.modbundle.app.utils.ModMetadataParser.parse((java.io.File) mod);

                    if (meta == null || meta.modId == null) {
                        if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get());
                        return;
                    }
                    final com.modbundle.app.utils.ModMetadata finalMeta = meta;
                    String fileName = (mod instanceof androidx.documentfile.provider.DocumentFile) ? ((androidx.documentfile.provider.DocumentFile) mod).getName() : ((java.io.File) mod).getName();

                    api.getVersions(finalMeta.modId, getSelectedVersion(), getSelectedLoader(),
                        versions -> {
                            if (versions != null && !versions.isEmpty()) {
                                com.modbundle.app.model.ModVersion latest = versions.get(0);
                                boolean alreadyLatest = false;
                                if (latest.files != null) {
                                    for (com.modbundle.app.model.ModVersion.VersionFile vf : latest.files) {
                                        if (vf.filename != null && vf.filename.equals(fileName)) { alreadyLatest = true; break; }
                                    }
                                }
                                if (!alreadyLatest) {
                                    finalMeta.hasUpdate = true;
                                    finalMeta.latestVersion = latest.versionNumber;
                                    com.modbundle.app.model.ModVersion.VersionFile f = com.modbundle.app.utils.ModDownloader.getPrimaryFile(latest);
                                    if (f != null) { finalMeta.latestFileUrl = f.url; finalMeta.latestFileName = f.filename; }
                                    updatesFound.incrementAndGet();
                                }
                            }
                            handler.post(() -> installedAdapter.updateMetaCache(fileName, finalMeta));
                            if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get());
                        },
                        error -> { if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get()); });
                } catch (Exception e) { if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get()); }
            }).start();
        }
    }

    private void finishCheckUpdates(int updatesFound) {
        handler.post(() -> {
            btnCheckUpdates.setEnabled(true);
            btnCheckUpdates.setText("Check Updates");
            layoutUpdateBar.setVisibility(updatesFound > 0 ? View.VISIBLE : View.GONE);
        });
    }

    private void performUpdate(Object mod, com.modbundle.app.utils.ModMetadata meta) {
        if (meta.latestFileUrl == null) return;
        com.modbundle.app.model.ModVersion.VersionFile file = new com.modbundle.app.model.ModVersion.VersionFile();
        file.url = meta.latestFileUrl; file.filename = meta.latestFileName; file.primary = true;
        if (mod instanceof androidx.documentfile.provider.DocumentFile) ((androidx.documentfile.provider.DocumentFile) mod).delete();
        else if (mod instanceof java.io.File) ((java.io.File) mod).delete();

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Updating...");
        progress.show();

        com.modbundle.app.utils.ModDownloader.DownloadCallback callback = new com.modbundle.app.utils.ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {}
            public void onSuccess(String fileName) {
                handler.post(() -> { progress.dismiss(); refreshInstalled(); });
            }
            public void onError(String error) { handler.post(() -> { progress.dismiss(); Toast.makeText(MainActivity.this, "Update failed", Toast.LENGTH_SHORT).show(); }); }
        };
        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null && "content".equals(instanceUri.getScheme())) downloader.downloadMod(file, instanceUri, "mods", null, "", "", callback);
        else {
            java.io.File instanceDir = getLegacyInstanceDir();
            if (instanceDir != null) downloader.downloadMod(file, new java.io.File(instanceDir, "mods"), null, "", "", callback);
        }
    }

    private void refreshInstalled() {
        try {
            installedMods.clear();
            Uri instanceUri = prefs.getInstanceUri();
            if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
                androidx.documentfile.provider.DocumentFile instanceDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, instanceUri);
                if (instanceDir != null) {
                    androidx.documentfile.provider.DocumentFile subDir = instanceDir.findFile(currentInstalledType);
                    if (subDir != null) {
                        for (androidx.documentfile.provider.DocumentFile f : subDir.listFiles()) {
                            String name = f.getName();
                            if (name != null && (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".disabled"))) installedMods.add(f);
                        }
                    }
                }
            } else {
                java.io.File instanceDir2 = getLegacyInstanceDir();
                if (instanceDir2 != null) {
                    java.io.File subDir = new java.io.File(instanceDir2, currentInstalledType);
                    if (subDir.exists()) {
                        java.io.File[] files = subDir.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) {
                                String name = f.getName();
                                if (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".disabled")) installedMods.add(f);
                            }
                        }
                    }
                }
            }
            installedAdapter.notifyDataSetChanged();
            if (tvInstalledCount != null) tvInstalledCount.setText(installedMods.size() + " files");
            emptyInstalled.setVisibility(installedMods.isEmpty() ? View.VISIBLE : View.GONE);
        } catch (Exception e) {}
    }

    private java.io.File getLegacyInstanceDir() {
        Uri uri = prefs.getInstanceUri();
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) return new java.io.File(uri.getPath());
        if ("content".equals(uri.getScheme())) {
            String path = getRealPathFromUri(uri);
            if (path != null) return new java.io.File(path);
        }
        return null;
    }

    private java.io.File getTargetDirLegacy() {
        java.io.File instanceDir = getLegacyInstanceDir();
        if (instanceDir == null) return null;
        String sub = "resourcepack".equals(currentProjectType) ? "resourcepacks" : "shader".equals(currentProjectType) ? "shaderpacks" : "mods";
        java.io.File target = new java.io.File(instanceDir, sub);
        if (!target.exists()) target.mkdirs();
        return target;
    }

    private String getRealPathFromUri(Uri uri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(uri);
            docId = java.net.URLDecoder.decode(docId, "UTF-8");
            String[] split = docId.split(":");
            if (split.length >= 2 && "primary".equalsIgnoreCase(split[0])) {
                return android.os.Environment.getExternalStorageDirectory() + "/" + split[1];
            }
        } catch (Exception e) {}
        return null;
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String realPath = getRealPathFromUri(uri);
            Uri uriToSave = (realPath != null) ? Uri.fromFile(new java.io.File(realPath)) : uri;
            prefs.saveInstanceUri(uriToSave);
            updateFolderLabel();
            refreshSavedPaths();
            searchMods(true);
        }
    }

    private void updateFolderLabel() {
        Uri uri = prefs.getInstanceUri();
        if (tvFolderPath != null) tvFolderPath.setText(uri != null ? uri.getLastPathSegment() : "No folder selected");
    }

    private void showFolderPickerPrompt() {
        new AlertDialog.Builder(this)
            .setTitle("Choose Folder")
            .setMessage("Select your instance folder.")
            .setPositiveButton("Choose", (d, w) -> openFolderPicker())
            .setNegativeButton("Later", null).show();
    }

    private void saveFilters() { prefs.saveFilters(getSelectedVersion(), getSelectedLoader()); }
    private String getSelectedVersion() { String v = (String) spinnerVersion.getSelectedItem(); return "Any".equals(v) ? "" : v; }
    private String getSelectedLoader() { String l = (String) spinnerLoader.getSelectedItem(); return "Any".equals(l) ? "" : l; }
}
