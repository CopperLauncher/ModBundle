package com.maxjubayeryt.modbundle;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.os.Environment;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maxjubayeryt.modbundle.api.ModrinthApi;
import com.maxjubayeryt.modbundle.api.CurseForgeApi;
import com.maxjubayeryt.modbundle.model.ModResult;
import com.maxjubayeryt.modbundle.model.ModVersion;
import com.maxjubayeryt.modbundle.model.SearchResponse;
import com.maxjubayeryt.modbundle.ui.InstalledModsAdapter;
import com.maxjubayeryt.modbundle.ui.ModAdapter;
import com.maxjubayeryt.modbundle.ui.InstanceAdapter;
import com.maxjubayeryt.modbundle.ui.VersionAdapter;
import android.view.LayoutInflater;
import java.util.ArrayList;
import com.maxjubayeryt.modbundle.utils.ModDownloader;
import com.maxjubayeryt.modbundle.utils.PrefManager;
import com.maxjubayeryt.modbundle.ModDetailActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_FOLDER = 3001;
    private static final int REQUEST_LOGO = 3002;

    // Views
    private View layoutBrowse, layoutInstalled, layoutSettings, layoutInstances;
    private EditText searchInput;
    private ImageButton btnFilter;
    private RecyclerView browseRecycler, installedRecycler;
    private TextView emptyBrowse, emptyInstalled, tvFolderPath;
    private View installedLoading;
    private ProgressBar browseProgress;
    private Button btnChooseFolder;

    // State
    private final List<ModResult> modResults = new ArrayList<>();
    private final List<Object> installedMods = new ArrayList<>();
    private ModAdapter modAdapter;
    private InstalledModsAdapter installedAdapter;

    private final ModrinthApi api = new ModrinthApi();
    private final CurseForgeApi curseForgeApi = new CurseForgeApi();
    private boolean hasMoreResults = true;
    private boolean useCurseForge = false;
    private String currentProjectType = "mod";
    // Replaced the old always-visible Modrinth/CurseForge + Mods/ResPacks/Shaders toggle
    // buttons and version/loader spinners with a single filter dialog (see
    // showFilterDialog()) — these two fields are now the source of truth for the
    // currently selected game version/loader instead of a live Spinner selection.
    private String currentGameVersion = "";
    private String currentLoader = "";
    private TextView installedTabMods, installedTabShaders, installedTabResourcepacks, tvInstalledCount;
    private String currentInstalledType = "mods";
    private android.widget.Button btnCheckUpdates;
    private android.widget.Button btnUpdateAll;
    private boolean includeSnapshots = false;
    private RecyclerView instancesRecycler;
    private String pendingLogoInstancePath;
    
    private InstanceAdapter instanceAdapter;
    private final java.util.List<InstanceAdapter.InstanceEntry> instanceList = new ArrayList<>();
    private ModDownloader downloader;
    private com.maxjubayeryt.modbundle.utils.InstanceNameStore instanceNameStore;
    private PrefManager prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Bounded pool for installed-list I/O and update checks — used instead of raw
    // `new Thread()` per call so opening the Installed tab, disabling a mod, and
    // "Check Updates" don't spawn dozens of threads at once (the thread-creation
    // overhead itself was a big chunk of the multi-second lag after those actions).
    private static final java.util.concurrent.ExecutorService sBgExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(3);

    private int currentOffset = 0;
    private String currentQuery = "";
    private boolean isLoading = false;

    private static final String[] LOADERS = {
        "Any", "fabric", "forge", "neoforge", "quilt"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        com.maxjubayeryt.modbundle.utils.ThemeManager.apply(this);
        setContentView(R.layout.activity_main);

        prefs = new PrefManager(this);
        downloader = new ModDownloader(this);
        instanceNameStore = new com.maxjubayeryt.modbundle.utils.InstanceNameStore(this);
        requestStoragePermissionIfNeeded();
        initViews();
        setupBottomNav();
        setupBrowseRecycler();
        setupFilters();
        setupSearch();
        btnFilter.setOnClickListener(v -> showFilterDialog());
        setupInstalledRecycler();
        setupSettings();
        requestManageStoragePermission();
        setupInstances();

        showTab("browse");

        // Prompt to set folder if not set
        if (!prefs.hasModsFolder()) {
            showFolderPickerPrompt();
        } else {
            updateFolderLabel();
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
        btnFilter       = findViewById(R.id.btn_filter);
        browseRecycler  = findViewById(R.id.browse_recycler);
        installedRecycler = findViewById(R.id.installed_recycler);
        emptyBrowse     = findViewById(R.id.empty_browse);
        emptyInstalled  = findViewById(R.id.empty_installed);
        installedLoading = findViewById(R.id.installed_loading);
        tvFolderPath    = findViewById(R.id.tv_folder_path);
        browseProgress  = findViewById(R.id.browse_progress);
        btnChooseFolder = findViewById(R.id.btn_choose_folder);
        installedTabMods = findViewById(R.id.installed_tab_mods);
        installedTabShaders = findViewById(R.id.installed_tab_shaders);
        installedTabResourcepacks = findViewById(R.id.installed_tab_resourcepacks);
        tvInstalledCount = findViewById(R.id.tv_installed_count);
        btnCheckUpdates = findViewById(R.id.btn_check_updates);
        btnUpdateAll = findViewById(R.id.btn_update_all);
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
        // An active instance's own saved loader/version (set via "Edit Instance") takes
        // priority over the generic last-used Browse filter for the *initial* selection.
        // This only ever reads from the instance — the filter dialog changing these
        // afterward never writes back into Instance Manager, that only happens through
        // the Edit Instance dialog itself (instanceNameStore.setLoader/setVersion), so
        // switching filters while browsing can't clobber what's saved for the instance.
        String instancePath = getActiveInstancePath();
        String instanceLoader = instancePath != null ? instanceNameStore.getLoader(instancePath) : "";
        String instanceVer = instancePath != null ? instanceNameStore.getVersion(instancePath) : "";
        currentLoader = !instanceLoader.isEmpty() ? instanceLoader : prefs.getLoader();
        currentGameVersion = !instanceVer.isEmpty() ? instanceVer : prefs.getGameVersion();
        if (prefs.hasModsFolder()) {
            searchMods(true);
        }
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
        instanceAdapter = new InstanceAdapter(this, instanceList, (instanceEntry, name) -> {
            android.net.Uri uri;
            String path = instanceEntry.path;
            if (path.startsWith("content://")) {
                uri = android.net.Uri.parse(path);
            } else {
                uri = android.net.Uri.fromFile(new java.io.File(path));
            }
            prefs.saveInstanceUri(uri);
            updateFolderLabel();
            updateActiveInstanceLabel();
            instanceAdapter.setActiveInstancePath(path);
            applyInstanceFilters(path);
            Toast.makeText(this, "Active: " + name, Toast.LENGTH_SHORT).show();
            // Stay on instances tab
        });

        // Wire rename/edit listener - name + loader + version
        instanceAdapter.setRenameListener((instance, currentName) -> {
            String path = instance.path;
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(48, 16, 48, 8);

            // Name
            android.widget.EditText etName = new android.widget.EditText(this);
            etName.setHint("Instance name");
            etName.setText(currentName);
            etName.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurface(etName));
            etName.setHintTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(etName));
            layout.addView(etName);

            // Loader label + spinner
            android.widget.TextView tvLoader = new android.widget.TextView(this);
            tvLoader.setText("Loader");
            tvLoader.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(tvLoader));
            tvLoader.setTextSize(12f);
            tvLoader.setPadding(0, 20, 0, 4);
            layout.addView(tvLoader);
            android.widget.Spinner spLoader = new android.widget.Spinner(this);
            android.widget.ArrayAdapter<String> lAd = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, LOADERS);
            lAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spLoader.setAdapter(lAd);
            String savedL = instanceNameStore.getLoader(path);
            for (int i = 0; i < LOADERS.length; i++) {
                if (LOADERS[i].equalsIgnoreCase(savedL)) { spLoader.setSelection(i); break; }
            }
            layout.addView(spLoader);

            // MC Version label + spinner
            android.widget.TextView tvVer = new android.widget.TextView(this);
            tvVer.setText("Minecraft Version");
            tvVer.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(tvVer));
            tvVer.setTextSize(12f);
            tvVer.setPadding(0, 20, 0, 4);
            layout.addView(tvVer);

            // Include snapshots checkbox
            android.widget.CheckBox cbSnap = new android.widget.CheckBox(this);
            cbSnap.setText("Include Snapshots");
            cbSnap.setTextColor(com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(cbSnap));
            CompoundButtonCompat.setButtonTintList(cbSnap, android.content.res.ColorStateList.valueOf(com.maxjubayeryt.modbundle.utils.ThemeColors.primary(cbSnap)));
            cbSnap.setChecked(false);
            layout.addView(cbSnap);

            android.widget.Spinner spVersion = new android.widget.Spinner(this);
            layout.addView(spVersion);

            // Load versions using current snapshots pref
            final boolean[] snapState = {false};
            java.util.List<String> verList = new java.util.ArrayList<>();
            verList.add("Any");
            android.widget.ArrayAdapter<String> verAd = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, verList);
            verAd.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spVersion.setAdapter(verAd);

            // Helper to reload versions
            final Runnable[] loadVersions = {null};
            loadVersions[0] = () -> {
                api.getGameVersions(snapState[0], versions -> {
                    handler.post(() -> {
                        verList.clear();
                        verList.add("Any");
                        verList.addAll(versions.subList(Math.min(1, versions.size()), versions.size()));
                        verAd.notifyDataSetChanged();
                        String savedV = instanceNameStore.getVersion(path);
                        for (int i = 0; i < verList.size(); i++) {
                            if (savedV.equals(verList.get(i))) { spVersion.setSelection(i); break; }
                        }
                    });
                }, err -> {});
            };
            loadVersions[0].run();

            cbSnap.setOnCheckedChangeListener((btn, isChecked) -> {
                snapState[0] = isChecked;
                loadVersions[0].run();
            });

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Edit Instance")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newLoader = spLoader.getSelectedItem().toString();
                    Object selV = spVersion.getSelectedItem();
                    String newVersion = selV != null ? selV.toString() : "";
                    if ("Any".equals(newLoader)) newLoader = "";
                    if ("Any".equals(newVersion)) newVersion = "";
                    if (!newName.isEmpty()) instanceNameStore.setName(path, newName);
                    instanceNameStore.setLoader(path, newLoader);
                    instanceNameStore.setVersion(path, newVersion);
                    instanceAdapter.notifyDataSetChanged();
                    updateActiveInstanceLabel();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // Wire logo picker listener
        instanceAdapter.setLogoListener((instance, path) -> {
            String[] logoNames = {"ic_fabric", "ic_quilt", "ic_forge", "ic_neoforge", "gallery"};
            String[] labels = {"Fabric", "Quilt", "Forge", "NeoForge", "Gallery"};

            ArrayAdapter<String> logoAdapter = new ArrayAdapter<>(this, android.R.layout.select_dialog_item, labels);

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Choose Logo")
                .setAdapter(logoAdapter, (d, which) -> {
                    if (logoNames[which].equals("gallery")) {
                        pendingLogoInstancePath = instance.path;
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/*");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(intent, REQUEST_LOGO);
                    } else {
                        instanceNameStore.setLogo(path, logoNames[which]);
                        instanceAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        instanceAdapter.setDeleteListener((instance, path) -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Remove instance")
                .setMessage("Remove this instance from the app? This will not delete the actual folder.")
                .setPositiveButton("Remove", (d, w) -> {
                    if (instance != null) {
                        instanceList.remove(instance);
                        if (path.equals(prefs.getInstanceUri() != null ? ("file".equals(prefs.getInstanceUri().getScheme()) ? prefs.getInstanceUri().getPath() : prefs.getInstanceUri().toString()) : "")) {
                            prefs.saveInstanceUri(null);
                            updateFolderLabel();
                            updateActiveInstanceLabel();
                        }
                        instanceAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        android.view.View instLayout = layoutInstances;
        if (instLayout != null) {
            instancesRecycler = instLayout.findViewById(R.id.instances_recycler);
            ImageButton btnAddInstance = instLayout.findViewById(R.id.btn_add_instance);

            if (instancesRecycler != null) {
                instancesRecycler.setLayoutManager(new LinearLayoutManager(this));
                instancesRecycler.setHasFixedSize(true);
                instancesRecycler.setNestedScrollingEnabled(false);
                instancesRecycler.setAdapter(instanceAdapter);
            }
            if (btnAddInstance != null) btnAddInstance.setOnClickListener(v -> openFolderPicker());
        }

        // Set active path on adapter and restore saved instance to the list
        android.net.Uri activeUri = prefs.getInstanceUri();
        if (activeUri != null) {
            addInstanceFromUri(activeUri);
            Uri preferred = resolvePreferredInstanceUri(activeUri);
            if (preferred != null) {
                String activePath = "file".equals(preferred.getScheme()) ? preferred.getPath() : activeUri.toString();
                instanceAdapter.setActiveInstancePath(activePath);
            }
        }
        updateActiveInstanceLabel();
    }

    private void addInstanceIfNotPresent(InstanceAdapter.InstanceEntry instanceEntry) {
        if (instanceEntry == null) return;
        String path = instanceEntry.path;
        boolean isContentUri = path.startsWith("content://");
        if (!isContentUri) {
            java.io.File instanceDir = new java.io.File(path);
            if (!instanceDir.exists() || !instanceDir.isDirectory()) return;
        }
        for (InstanceAdapter.InstanceEntry entry : instanceList) {
            if (entry.path.equals(path)) return;
        }
        instanceList.add(instanceEntry);
        instanceAdapter.notifyDataSetChanged();
    }

    private Uri resolvePreferredInstanceUri(Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) return uri;
        if ("content".equals(uri.getScheme())) {
            String realPath = getRealPathFromUri(uri);
            if (realPath != null) {
                java.io.File instanceDir = new java.io.File(realPath);
                if (instanceDir.exists() && instanceDir.isDirectory()) {
                    return Uri.fromFile(instanceDir);
                }
            }
        }
        return uri;
    }

    /**
     * Resolves the currently-active instance's storage path, the same way
     * setupInstances() does for the instance list's "active" highlight — pulled out
     * here so Browse's initial filter setup can use it too, without depending on
     * setupInstances() having already populated the instance list UI.
     */
    private String getActiveInstancePath() {
        Uri activeUri = prefs.getInstanceUri();
        if (activeUri == null) return null;
        Uri preferred = resolvePreferredInstanceUri(activeUri);
        if (preferred == null) return null;
        return "file".equals(preferred.getScheme()) ? preferred.getPath() : activeUri.toString();
    }

    private void addInstanceFromUri(Uri uri) {
        if (uri == null) return;
        Uri preferred = resolvePreferredInstanceUri(uri);
        if (preferred != null && "file".equals(preferred.getScheme())) {
            addInstanceIfNotPresent(new InstanceAdapter.InstanceEntry(preferred.getPath(), false));
            return;
        }
        if (uri.toString().startsWith("content://")) {
            addInstanceIfNotPresent(new InstanceAdapter.InstanceEntry(uri.toString(), true));
        }
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
            String display = (customName != null && !customName.isEmpty()) ? customName : getUriDisplayName(uri);
            if (display == null) display = uri.toString();
            tvActive.setText("Active: " + display);
        }
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
        modAdapter = new ModAdapter(this, modResults, new com.maxjubayeryt.modbundle.ui.ModAdapter.OnInstallClickListener() {
            public void onInstallClick(com.maxjubayeryt.modbundle.model.ModResult mod) {
                if (!prefs.hasInstanceFolder()) { showFolderPickerPrompt(); return; }
                showInstallDialog(mod);
            }
            public void onModClick(com.maxjubayeryt.modbundle.model.ModResult mod) {
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
        browseRecycler.setHasFixedSize(true);
        browseRecycler.setNestedScrollingEnabled(false);
        browseRecycler.setAdapter(modAdapter);
        browseRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || isLoading || !hasMoreResults) return;
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;
                int totalItemCount = layoutManager.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (lastVisibleItem >= totalItemCount - 2) {
                    searchMods(false);
                }
            }
        });
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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
        installedAdapter.setOnSwitchVersionListener(this::showSwitchVersionDialog);

        btnCheckUpdates.setOnClickListener(v -> checkUpdates());
        btnUpdateAll.setOnClickListener(v -> {
            for (Object mod : new java.util.ArrayList<>(installedMods)) {
                String fileName = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) mod).getName() : ((java.io.File) mod).getName();
                com.maxjubayeryt.modbundle.utils.ModMetadata meta = fileName != null ? installedAdapter.getMetaCache().get(fileName) : null;
                if (meta != null && meta.hasUpdate) performUpdate(mod, meta);
            }
        });

        installedRecycler.setLayoutManager(new LinearLayoutManager(this));
        installedRecycler.setHasFixedSize(true);
        installedRecycler.setNestedScrollingEnabled(false);
        installedRecycler.setAdapter(installedAdapter);
    }

    private void switchInstalledTab() {
        installedTabMods.setTextColor("mods".equals(currentInstalledType) ? com.maxjubayeryt.modbundle.utils.ThemeColors.primary(installedTabMods) : com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(installedTabMods));
        installedTabMods.setTypeface(null, "mods".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedTabShaders.setTextColor("shaderpacks".equals(currentInstalledType) ? com.maxjubayeryt.modbundle.utils.ThemeColors.primary(installedTabShaders) : com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(installedTabShaders));
        installedTabShaders.setTypeface(null, "shaderpacks".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedTabResourcepacks.setTextColor("resourcepacks".equals(currentInstalledType) ? com.maxjubayeryt.modbundle.utils.ThemeColors.primary(installedTabResourcepacks) : com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(installedTabResourcepacks));
        installedTabResourcepacks.setTypeface(null, "resourcepacks".equals(currentInstalledType) ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        installedAdapter.setShowDisable("mods".equals(currentInstalledType));
        installedAdapter.setCurrentType(currentInstalledType);
        installedAdapter.notifyDataSetChanged();
    }

    private void setupSettings() {
        btnChooseFolder.setOnClickListener(v -> openFolderPicker());
        updateFolderLabel();

        Spinner spTheme = findViewById(R.id.spinner_theme);
        if (spTheme != null) {
            String[] themeOptions = {"System Default", "Light", "Dark"};
            ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeOptions);
            themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spTheme.setAdapter(themeAdapter);
            spTheme.setSelection(prefs.getThemeMode());
            spTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean ready = false;
                public void onItemSelected(AdapterView<?> a, View v, int position, long id) {
                    if (!ready) { ready = true; return; }
                    prefs.saveThemeMode(position);
                    // Recreating applies the new night mode immediately without losing the
                    // current tab/instance/search state (onCreate re-reads all of that).
                    com.maxjubayeryt.modbundle.ModBundleApp.applyThemeMode(position);
                    recreate();
                }
                public void onNothingSelected(AdapterView<?> a) {}
            });
        }

        // Color scheme: Material You dynamic color (wallpaper-derived, Android 12+ only)
        // vs. a manual preset that works on any Android version — see ThemeManager.
        boolean dynamicAvailable = com.google.android.material.color.DynamicColors.isDynamicColorAvailable();
        com.google.android.material.materialswitch.MaterialSwitch swDynamic = findViewById(R.id.switch_dynamic_color);
        Spinner spColorPreset = findViewById(R.id.spinner_color_preset);
        if (swDynamic != null) {
            swDynamic.setEnabled(dynamicAvailable);
            swDynamic.setChecked(dynamicAvailable && prefs.getUseDynamicColor());
            if (!dynamicAvailable) {
                swDynamic.setText("Material You needs Android 12+");
            }
            swDynamic.setOnCheckedChangeListener((btn, checked) -> {
                if (!btn.isPressed()) return; // ignore the setChecked() call above
                prefs.saveUseDynamicColor(checked);
                recreate();
            });
        }
        if (spColorPreset != null) {
            String[] presetOptions = {"Purple (default)", "Blue", "Green", "Orange", "Pink"};
            ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presetOptions);
            presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spColorPreset.setAdapter(presetAdapter);
            spColorPreset.setSelection(prefs.getColorPreset());
            spColorPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                boolean ready = false;
                public void onItemSelected(AdapterView<?> a, View v, int position, long id) {
                    if (!ready) { ready = true; return; }
                    prefs.saveColorPreset(position);
                    recreate();
                }
                public void onNothingSelected(AdapterView<?> a) {}
            });
        }

        int[] linkIds = {
            R.id.tv_link_youtube, R.id.tv_github_link, R.id.tv_link_kofi,
            R.id.tv_link_patreon, R.id.tv_discord_link,
            R.id.tv_link_copper_github, R.id.tv_link_modrinth
        };
        for (int id : linkIds) {
            android.view.View lv = findViewById(id);
            if (lv != null) {
                lv.setOnClickListener(view -> {
                    Object tag = view.getTag();
                    if (tag != null) {
                        startActivity(new android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(tag.toString())));
                    }
                });
            }
        }
    }

    private void searchMods(boolean reset) {
        if (isLoading) return;
        if (!reset && !hasMoreResults) return;
        if (reset) {
            currentOffset = 0;
            modResults.clear();
            modAdapter.notifyDataSetChanged();
            hasMoreResults = true;
        }

        isLoading = true;
        browseProgress.setVisibility(View.VISIBLE);
        emptyBrowse.setVisibility(View.GONE);

        currentQuery = searchInput.getText().toString().trim();
        String version = getSelectedVersion();
        // Resource packs and shader packs have no mod-loader tag on Modrinth/CurseForge —
        // their versions aren't Fabric/Forge/etc-specific — so passing the loader filter
        // for them returns zero results instead of "no filter applied". Only mods use it.
        String loader  = "mod".equals(currentProjectType) ? getSelectedLoader() : "";

        if (useCurseForge) {
            curseForgeApi.searchMods(currentQuery, version, loader, currentOffset, currentProjectType, results -> {
                runOnUiThread(() -> {
                    browseProgress.setVisibility(android.view.View.GONE);
                    isLoading = false;
                    if (reset) { modAdapter.getMods().clear(); modAdapter.notifyDataSetChanged(); }
                    if (results.isEmpty()) {
                        hasMoreResults = false;
                        if (modAdapter.getItemCount() == 0) emptyBrowse.setVisibility(android.view.View.VISIBLE);
                    } else {
                        emptyBrowse.setVisibility(android.view.View.GONE);
                        modAdapter.getMods().addAll(results); modAdapter.notifyDataSetChanged();
                        currentOffset += results.size();
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
                        hasMoreResults = currentOffset < result.totalHits;
                    } else {
                        hasMoreResults = false;
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
        com.maxjubayeryt.modbundle.ui.M3ProgressDialog loading = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        loading.setMessage("Fetching versions…");
        loading.show();

        if ("curseforge".equals(mod.source)) {
            curseForgeApi.getLatestFile(mod.projectId, version, loader, fileObj -> {
                handler.post(() -> {
                    loading.dismiss();
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
                    curseForgeApi.getDownloadUrl(mod.projectId, fileId, url -> {
                        handler.post(() -> {
                            if (url == null || url.isEmpty()) {
                                Toast.makeText(this, "Unable to download file", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Install: " + mod.title)
                                .setMessage(fileName)
                                .setPositiveButton("Install", (d, w) -> {
                                    ModVersion.VersionFile file = new ModVersion.VersionFile();
                                    file.url = url;
                                    file.filename = fileName;
                                    ModVersion fakeVersion = new ModVersion();
                                    fakeVersion.versionNumber = fileName;
                                    fakeVersion.dependencies = new java.util.ArrayList<>();
                                    showDependencySelectionDialog(mod, fakeVersion, file);
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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Install: " + mod.title)
                    .setItems(labels, (d, which) -> {
                        ModVersion selected = versions.get(which);
                        ModVersion.VersionFile file = ModDownloader.getPrimaryFile(selected);
                        if (file != null) showDependencySelectionDialog(mod, selected, file);
                    })
                    .setNegativeButton("Cancel", null).show();
            });
        }, error -> handler.post(() -> { loading.dismiss(); Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show(); }));
    }

    private void startDownload(ModResult mod, ModVersion version, ModVersion.VersionFile file) {
        startDownload(mod, version, file, version.dependencies);
    }

    private void startDownload(ModResult mod, ModVersion version, ModVersion.VersionFile file, List<ModVersion.Dependency> dependencies) {
        com.maxjubayeryt.modbundle.ui.M3ProgressDialog progress = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        progress.setTitle("Installing " + mod.title);
        progress.setMessage("Downloading…");
        progress.setProgressStyle(com.maxjubayeryt.modbundle.ui.M3ProgressDialog.STYLE_HORIZONTAL);
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
        // Use instance-stored loader/version for dependency downloads
        String depVersion = "", depLoader = "";
        Uri depUri = prefs.getInstanceUri();
        if (depUri != null) {
            String depPath = "file".equals(depUri.getScheme()) ? depUri.getPath() : depUri.toString();
            if (depPath != null) {
                depLoader = instanceNameStore.getLoader(depPath);
                depVersion = instanceNameStore.getVersion(depPath);
            }
        }
        String dependencyGameVersion = depVersion.isEmpty() ? getSelectedVersion() : depVersion;
        String dependencyLoader = depLoader.isEmpty() ? getSelectedLoader() : depLoader;
        // Override with actual mod's version/loader if more specific
        if (version.gameVersions != null && !version.gameVersions.isEmpty() && !version.gameVersions.get(0).isEmpty()) {
            dependencyGameVersion = version.gameVersions.get(0);
        }
        if (version.loaders != null && !version.loaders.isEmpty() && !version.loaders.get(0).isEmpty()) {
            dependencyLoader = version.loaders.get(0);
        }
        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, subFolder, dependencies, dependencyGameVersion, dependencyLoader, callback);
        } else {
            java.io.File targetDir = getTargetDirLegacy();
            if (targetDir == null) { progress.dismiss(); showFolderPickerPrompt(); return; }
            downloader.downloadMod(file, targetDir, dependencies, dependencyGameVersion, dependencyLoader, callback);
        }
    }

    private void showDependencySelectionDialog(ModResult mod, ModVersion version, ModVersion.VersionFile file) {
        if (version.dependencies == null || version.dependencies.isEmpty()) {
            startDownload(mod, version, file);
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
            startDownload(mod, version, file);
            return;
        }

        CharSequence[] items = labels.toArray(new CharSequence[0]);
        boolean[] initialChecked = new boolean[checked.size()];
        for (int i = 0; i < checked.size(); i++) initialChecked[i] = checked.get(i);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Install dependencies")
            .setMessage("Select which dependencies to install with this mod.")
            .setMultiChoiceItems(items, initialChecked, (dialog, which, isChecked) -> initialChecked[which] = isChecked)
            .setPositiveButton("Install selected", (d, w) -> {
                List<ModVersion.Dependency> selectedDeps = new ArrayList<>();
                for (int i = 0; i < deps.size(); i++) {
                    if (initialChecked[i]) selectedDeps.add(deps.get(i));
                }
                startDownload(mod, version, file, selectedDeps);
            })
            .setNeutralButton("Install without deps", (d, w) -> startDownload(mod, version, file, new ArrayList<>()))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void checkUpdates() {
        btnCheckUpdates.setEnabled(false);
        btnCheckUpdates.setText("Checking...");
        if (btnUpdateAll != null) btnUpdateAll.setVisibility(View.GONE);
        installedAdapter.getMetaCache().clear();
        installedAdapter.notifyDataSetChanged();

        java.util.List<Object> modsCopy = new java.util.ArrayList<>(installedMods);
        if (modsCopy.isEmpty()) {
            finishCheckUpdates(0);
            return;
        }
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(modsCopy.size());
        java.util.concurrent.atomic.AtomicInteger updatesFound = new java.util.concurrent.atomic.AtomicInteger(0);

        // Instance's stored loader/game-version take priority, falling back to whatever
        // the Browse spinners currently have.
        String iMcVer = "", iLoader = "";
        android.net.Uri iUri = prefs.getInstanceUri();
        if (iUri != null) {
            String ipath = "file".equals(iUri.getScheme()) ? iUri.getPath() : iUri.toString();
            if (ipath != null) { iLoader = instanceNameStore.getLoader(ipath); iMcVer = instanceNameStore.getVersion(ipath); }
        }
        if (iMcVer.isEmpty()) iMcVer = getSelectedVersion();
        if (iLoader.isEmpty()) iLoader = getSelectedLoader();
        // Resource packs and shader packs don't carry mod-loader tags on Modrinth (their
        // versions are loader-less), so filtering their update lookup by e.g. "fabric"
        // silently returns zero matches — which is why updates never showed up for those
        // two content types. Only mods get the loader filter.
        if (!"mods".equals(currentInstalledType)) iLoader = "";
        final String checkVer = iMcVer;
        final String checkLoad = iLoader;

        com.maxjubayeryt.modbundle.utils.ContentUpdateChecker checker = new com.maxjubayeryt.modbundle.utils.ContentUpdateChecker();

        for (Object mod : modsCopy) {
            sBgExecutor.execute(() -> {
                String fileName = (mod instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) mod).getName() : ((java.io.File) mod).getName();
                if (fileName != null && fileName.endsWith(".disabled")) {
                    if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get());
                    return;
                }

                // Hash-based lookup (SHA1 -> Modrinth, murmur2 -> CurseForge fallback) works
                // identically for mods, resource packs, and shader packs — unlike the old
                // ModMetadataParser-only approach, it doesn't depend on the file carrying its
                // own embedded project id, which resource/shader packs never do. This is what
                // makes update checking actually work for those two content types.
                com.maxjubayeryt.modbundle.utils.ContentUpdateChecker.ResultCallback onResult = result -> {
                    if (result != null && result.hasUpdate && fileName != null) {
                        com.maxjubayeryt.modbundle.utils.ModMetadata meta = new com.maxjubayeryt.modbundle.utils.ModMetadata();
                        meta.hasUpdate = true;
                        meta.latestVersion = result.latestVersionName;
                        meta.latestFileUrl = result.latestFileUrl;
                        meta.latestFileName = result.latestFileName;
                        updatesFound.incrementAndGet();
                        handler.post(() -> {
                            installedAdapter.getMetaCache().put(fileName, meta);
                            scheduleMetaCacheRefresh();
                        });
                    }
                    if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get());
                };

                try {
                    if (mod instanceof androidx.documentfile.provider.DocumentFile) {
                        checker.check(this, (androidx.documentfile.provider.DocumentFile) mod, checkVer, checkLoad, onResult);
                    } else {
                        checker.check((java.io.File) mod, checkVer, checkLoad, onResult);
                    }
                } catch (Exception e) { if (pending.decrementAndGet() <= 0) finishCheckUpdates(updatesFound.get()); }
            });
        }
    }

    private void finishCheckUpdates(int updatesFound) {
        handler.post(() -> {
            if (btnCheckUpdates != null) { btnCheckUpdates.setEnabled(true); btnCheckUpdates.setText("Check Updates"); }
            if (btnUpdateAll != null) btnUpdateAll.setVisibility(updatesFound > 0 ? View.VISIBLE : View.GONE);
            if (pendingMetaCacheRefresh != null) { handler.removeCallbacks(pendingMetaCacheRefresh); pendingMetaCacheRefresh = null; }
            installedAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Lets the user switch an already-installed mod, resource pack, or shader pack to a
     * different version — ported from Copper-Android's ModVersionListFragment version
     * picker, generalized here to work for all three content types (Copper only offers
     * it for mods). Resolves the installed file's Modrinth project via
     * {@link com.maxjubayeryt.modbundle.utils.ContentUpdateChecker} (hash-based, so it works even
     * for resource/shader packs that carry no embedded project id), then reuses the same
     * VersionAdapter the browse screen uses to list and pick a version to switch to.
     */
    private void showSwitchVersionDialog(Object mod) {
        String fileName = (mod instanceof androidx.documentfile.provider.DocumentFile)
                ? ((androidx.documentfile.provider.DocumentFile) mod).getName()
                : ((java.io.File) mod).getName();

        com.maxjubayeryt.modbundle.ui.M3ProgressDialog resolving = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        resolving.setTitle("Looking up " + fileName + "\u2026");
        resolving.setMessage("Identifying content on Modrinth/CurseForge");
        resolving.setCancelable(true);
        resolving.show();

        com.maxjubayeryt.modbundle.utils.ContentUpdateChecker checker = new com.maxjubayeryt.modbundle.utils.ContentUpdateChecker();

        // Reuse the checker's own hash resolution instead of duplicating it: ask it for
        // "updates" against an empty game version/loader filter, which — because it hits
        // Modrinth's version_file-by-hash endpoint first — also gives us the project id
        // via the version list that comes back.
        java.util.function.BiConsumer<String, String> openVersionList = (projectId, source) -> {
            resolving.dismiss();
            if (projectId == null) {
                Toast.makeText(this, "Couldn't identify this file on Modrinth or CurseForge", Toast.LENGTH_LONG).show();
                return;
            }
            api.getVersions(projectId, getSelectedVersion(), getSelectedLoader(), versions -> handler.post(() -> {
                if (versions == null || versions.isEmpty()) {
                    Toast.makeText(this, "No versions found for this content", Toast.LENGTH_SHORT).show();
                    return;
                }
                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_version_list, null);
                RecyclerView recycler = dialogView.findViewById(R.id.detail_versions_recycler);
                recycler.setLayoutManager(new LinearLayoutManager(this));
                AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Switch version")
                        .setView(dialogView)
                        .setNegativeButton("Cancel", null)
                        .create();
                VersionAdapter adapter = new VersionAdapter(versions, (version, file) -> {
                    dialog.dismiss();
                    switchInstalledContentVersion(mod, fileName, file);
                });
                recycler.setAdapter(adapter);
                dialog.show();
            }), error -> handler.post(() ->
                    Toast.makeText(this, "Failed to load versions: " + error, Toast.LENGTH_SHORT).show()));
        };

        if (mod instanceof androidx.documentfile.provider.DocumentFile) {
            checker.check(this, (androidx.documentfile.provider.DocumentFile) mod, "", "", result ->
                    resolveProjectIdThen((androidx.documentfile.provider.DocumentFile) mod, null, openVersionList));
        } else {
            checker.check((java.io.File) mod, "", "", result ->
                    resolveProjectIdThen(null, (java.io.File) mod, openVersionList));
        }
    }

    /**
     * ContentUpdateChecker.Result deliberately doesn't expose the resolved project id
     * (it's only meant for update/icon lookups), so for "switch version" we re-hash the
     * file here and query Modrinth's version_file endpoint directly to get it — the same
     * lookup ContentUpdateChecker does internally, kept in one place there and reused
     * here to avoid growing that class's public surface just for this dialog.
     */
    private void resolveProjectIdThen(androidx.documentfile.provider.DocumentFile safFile, java.io.File plainFile,
                                       java.util.function.BiConsumer<String, String> callback) {
        new Thread(() -> {
            try {
                java.io.InputStream is = safFile != null
                        ? getContentResolver().openInputStream(safFile.getUri())
                        : new java.io.FileInputStream(plainFile);
                if (is == null) { handler.post(() -> callback.accept(null, null)); return; }
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int read;
                while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
                is.close();
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] digest = md.digest(bos.toByteArray());
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));

                api.getVersionFromHash(sb.toString(),
                        version -> handler.post(() -> callback.accept(version.projectId, "modrinth")),
                        err -> handler.post(() -> callback.accept(null, null)));
            } catch (Exception e) {
                handler.post(() -> callback.accept(null, null));
            }
        }).start();
    }

    /** Downloads the picked version into the same subfolder/disabled-state as the file it replaces. */
    private void switchInstalledContentVersion(Object mod, String oldFileName, ModVersion.VersionFile newFile) {
        boolean wasDisabled = oldFileName.endsWith(".disabled");
        String targetName = wasDisabled ? newFile.filename + ".disabled" : newFile.filename;

        com.maxjubayeryt.modbundle.ui.M3ProgressDialog progress = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        progress.setTitle("Switching version\u2026");
        progress.show();

        ModDownloader.DownloadCallback callback = new ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) { handler.post(() -> progress.setMessage(percent + "%")); }
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    progress.dismiss();
                    if (mod instanceof androidx.documentfile.provider.DocumentFile) ((androidx.documentfile.provider.DocumentFile) mod).delete();
                    else if (mod instanceof java.io.File) ((java.io.File) mod).delete();
                    refreshInstalled();
                    Toast.makeText(MainActivity.this, "Switched to " + newFile.filename, Toast.LENGTH_SHORT).show();
                });
            }
            public void onError(String error) {
                handler.post(() -> { progress.dismiss(); Toast.makeText(MainActivity.this, "Switch failed: " + error, Toast.LENGTH_SHORT).show(); });
            }
        };

        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null) {
            downloader.downloadMod(newFile, instanceUri, currentInstalledType, null, getSelectedVersion(), getSelectedLoader(), callback);
        } else {
            java.io.File instanceDir = getLegacyInstanceDir();
            if (instanceDir == null) { progress.dismiss(); Toast.makeText(this, "No instance folder set", Toast.LENGTH_SHORT).show(); return; }
            java.io.File subDir = new java.io.File(instanceDir, currentInstalledType);
            downloader.downloadMod(newFile, subDir, null, getSelectedVersion(), getSelectedLoader(), callback);
        }
    }

    private void performUpdate(Object mod, com.maxjubayeryt.modbundle.utils.ModMetadata meta) {
        if (meta.latestFileUrl == null) return;
        com.maxjubayeryt.modbundle.model.ModVersion.VersionFile file = new com.maxjubayeryt.modbundle.model.ModVersion.VersionFile();
        file.url = meta.latestFileUrl; file.filename = meta.latestFileName; file.primary = true;
        if (mod instanceof androidx.documentfile.provider.DocumentFile) ((androidx.documentfile.provider.DocumentFile) mod).delete();
        else if (mod instanceof java.io.File) ((java.io.File) mod).delete();

        com.maxjubayeryt.modbundle.ui.M3ProgressDialog progress = new com.maxjubayeryt.modbundle.ui.M3ProgressDialog(this);
        progress.setTitle("Updating...");
        progress.show();

        com.maxjubayeryt.modbundle.utils.ModDownloader.DownloadCallback callback = new com.maxjubayeryt.modbundle.utils.ModDownloader.DownloadCallback() {
            public void onProgress(String fileName, int percent) {}
            public void onSuccess(String fileName) {
                handler.post(() -> {
                    progress.dismiss();
                    // Remove this mod's update entry from cache
                    installedAdapter.getMetaCache().remove(meta.latestFileName);
                    refreshInstalled();
                    Toast.makeText(MainActivity.this, "Updated!", Toast.LENGTH_SHORT).show();
                });
            }
            public void onError(String error) { handler.post(() -> { progress.dismiss(); Toast.makeText(MainActivity.this, "Update failed", Toast.LENGTH_SHORT).show(); }); }
        };
        // Get instance loader/version for correct update download
        String upLoader = "", upVersion = "";
        Uri instanceUri = prefs.getInstanceUri();
        if (instanceUri != null) {
            String ipath = "file".equals(instanceUri.getScheme()) ? instanceUri.getPath() : instanceUri.toString();
            if (ipath != null) {
                upLoader = instanceNameStore.getLoader(ipath);
                upVersion = instanceNameStore.getVersion(ipath);
            }
        }
        final String finalUpLoader = upLoader.isEmpty() ? getSelectedLoader() : upLoader;
        final String finalUpVersion = upVersion.isEmpty() ? getSelectedVersion() : upVersion;

        if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
            downloader.downloadMod(file, instanceUri, "mods", null, finalUpVersion, finalUpLoader, callback);
        } else {
            java.io.File instanceDir = getLegacyInstanceDir();
            if (instanceDir != null) downloader.downloadMod(file, new java.io.File(instanceDir, "mods"), null, finalUpVersion, finalUpLoader, callback);
        }
    }

    private void refreshInstalled() {
        // listFiles() on SAF DocumentFile trees (and even plain File I/O for large mod
        // folders) is genuinely slow — each call is effectively a synchronous IPC round
        // trip to the DocumentsProvider. Running it on the main thread is why opening the
        // Installed tab (and refreshing it after every disable/update) used to freeze the
        // UI. It's moved to a background executor here; the tab this result belongs to is
        // captured up front so a stale result from a since-abandoned tab switch is dropped
        // instead of overwriting the list for whichever tab is now showing.
        final String requestedType = currentInstalledType;
        // Only show the loading placeholder for a genuinely empty first load. For a quick
        // refresh after pausing/updating/deleting one item, the list already has data —
        // showing the recycler AND the loading placeholder at the same time (both as
        // weight=1 siblings) is exactly what was splitting the screen in half with a
        // spinner floating below a truncated list. Those quick refreshes now just update
        // the list in place with no visual interruption at all.
        boolean coldLoad = installedMods.isEmpty();
        if (coldLoad) {
            installedLoading.setVisibility(View.VISIBLE);
            installedRecycler.setVisibility(View.GONE);
            emptyInstalled.setVisibility(View.GONE);
        }
        sBgExecutor.execute(() -> {
            List<Object> collected = new ArrayList<>();
            try {
                Uri instanceUri = prefs.getInstanceUri();
                if (instanceUri != null && "content".equals(instanceUri.getScheme())) {
                    androidx.documentfile.provider.DocumentFile instanceDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, instanceUri);
                    if (instanceDir != null) {
                        androidx.documentfile.provider.DocumentFile subDir = instanceDir.findFile(requestedType);
                        if (subDir != null) {
                            for (androidx.documentfile.provider.DocumentFile f : subDir.listFiles()) {
                                String name = f.getName();
                                if (name != null && (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".disabled"))) collected.add(f);
                            }
                        }
                    }
                } else {
                    java.io.File instanceDir2 = getLegacyInstanceDir();
                    if (instanceDir2 != null) {
                        java.io.File subDir = new java.io.File(instanceDir2, requestedType);
                        if (subDir.exists()) {
                            java.io.File[] files = subDir.listFiles();
                            if (files != null) {
                                for (java.io.File f : files) {
                                    String name = f.getName();
                                    if (name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".disabled")) collected.add(f);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) { /* fall through with whatever was collected */ }

            // listFiles()/DocumentFile enumeration order isn't stable — it reflects filesystem/SAF
            // directory order, which can (and does) change after a rename. Disabling/enabling a
            // mod renames it (adds/removes ".disabled"), so without an explicit sort here the row
            // jumps to wherever the provider now happens to list it. Sorting by filename keeps the
            // list in the same order across refreshes regardless of enumeration order.
            collected.sort((a, b) -> {
                String nameA = (a instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) a).getName() : ((java.io.File) a).getName();
                String nameB = (b instanceof androidx.documentfile.provider.DocumentFile)
                        ? ((androidx.documentfile.provider.DocumentFile) b).getName() : ((java.io.File) b).getName();
                if (nameA == null) nameA = "";
                if (nameB == null) nameB = "";
                return nameA.compareToIgnoreCase(nameB);
            });

            handler.post(() -> {
                if (!requestedType.equals(currentInstalledType)) return; // user switched tabs while this was loading
                installedLoading.setVisibility(View.GONE);
                installedRecycler.setVisibility(View.VISIBLE);
                installedMods.clear();
                installedMods.addAll(collected);
                installedAdapter.notifyDataSetChanged();
                if (tvInstalledCount != null) tvInstalledCount.setText(installedMods.size() + " files");
                emptyInstalled.setVisibility(installedMods.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
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

    private String getRealPathFromUri(Uri uri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(uri);
            if (docId == null) return null;
            docId = java.net.URLDecoder.decode(docId, "UTF-8");
            if (docId.startsWith("raw:")) {
                return docId.substring(4);
            }
            String[] split = docId.split(":", 2);
            if (split.length == 0) return null;
            String volume = split[0];
            String pathPart = split.length > 1 ? split[1] : "";
            if (volume.isEmpty()) return null;
            if ("primary".equalsIgnoreCase(volume)) {
                if (pathPart.isEmpty()) return android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
                return android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + pathPart;
            }
            if (pathPart.isEmpty()) {
                return "/storage/" + volume;
            }
            return "/storage/" + volume + "/" + pathPart;
        } catch (Exception e) {}
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

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            Uri preferred = resolvePreferredInstanceUri(uri);
            if (preferred != null && "file".equals(preferred.getScheme())) {
                java.io.File instanceDir = new java.io.File(preferred.getPath());
                if (instanceDir.exists() && instanceDir.isDirectory()) {
                    prefs.saveInstanceUri(preferred);
                    addInstanceIfNotPresent(new InstanceAdapter.InstanceEntry(instanceDir.getAbsolutePath(), false));
                    instanceAdapter.setActiveInstancePath(instanceDir.getAbsolutePath());
                    updateFolderLabel();
                    updateActiveInstanceLabel();
                    searchMods(true);
                    return;
                }
            }

            // Fallback to SAF if file path cannot be resolved
            String realPath = getRealPathFromUri(uri);
            if (realPath != null) {
                java.io.File instanceDir = new java.io.File(realPath);
                if (instanceDir.exists() && instanceDir.isDirectory()) {
                    prefs.saveInstanceUri(uri);
                    addInstanceIfNotPresent(new InstanceAdapter.InstanceEntry(instanceDir.getAbsolutePath(), false));
                    instanceAdapter.setActiveInstancePath(instanceDir.getAbsolutePath());
                    updateFolderLabel();
                    updateActiveInstanceLabel();
                    searchMods(true);
                    return;
                }
            }
            prefs.saveInstanceUri(uri);
            addInstanceIfNotPresent(new InstanceAdapter.InstanceEntry(uri.toString(), true));
            instanceAdapter.setActiveInstancePath(uri.toString());
            updateFolderLabel();
            updateActiveInstanceLabel();
            searchMods(true);
            return;
        }

        if (requestCode == REQUEST_LOGO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null || pendingLogoInstancePath == null) return;
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            instanceNameStore.setLogo(pendingLogoInstancePath, uri.toString());
            instanceAdapter.notifyDataSetChanged();
            pendingLogoInstancePath = null;
        }
    }

    private void updateFolderLabel() {
        Uri uri = prefs.getInstanceUri();
        if (tvFolderPath == null) return;
        if (uri == null) {
            tvFolderPath.setText("No folder selected");
            return;
        }
        String label = uri.getLastPathSegment();
        if (label == null || label.isEmpty()) {
            label = getUriDisplayName(uri);
        }
        tvFolderPath.setText(label != null ? label : uri.toString());
    }

    private String getUriDisplayName(Uri uri) {
        if (uri == null) return null;
        try {
            androidx.documentfile.provider.DocumentFile file = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri);
            if (file != null && file.getName() != null) return file.getName();
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private void showFolderPickerPrompt() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Choose Folder")
            .setMessage("Select your instance folder.")
            .setPositiveButton("Choose", (d, w) -> openFolderPicker())
            .setNegativeButton("Later", null).show();
    }

    /**
     * Syncs the browse screen's loader/game-version spinners (and therefore search/update
     * filtering) to whatever loader+version were saved for this instance, so switching
     * instances doesn't leave Browse filtered against the previous instance's loader/MC
     * version. Falls back to leaving the spinners untouched when the instance has no
     * saved loader/version yet (e.g. it was never edited via "Edit Instance").
     */
    private void applyInstanceFilters(String path) {
        String savedLoader = instanceNameStore.getLoader(path);
        String savedVersion = instanceNameStore.getVersion(path);
        boolean changed = false;
        if (savedLoader != null && !savedLoader.isEmpty()) { currentLoader = savedLoader; changed = true; }
        if (savedVersion != null && !savedVersion.isEmpty()) { currentGameVersion = savedVersion; changed = true; }
        if (changed) { saveFilters(); searchMods(true); }
    }

    /**
     * Replaces the old always-visible Modrinth/CurseForge toggle, Mods/Res Packs/Shaders
     * toggle, and version/loader spinners with a single dialog opened from the filter
     * icon next to the search bar. All the underlying state (useCurseForge,
     * currentProjectType, currentGameVersion, currentLoader, includeSnapshots) is
     * unchanged — only how it's edited moved.
     */
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filters, null);
        Spinner spSource = dialogView.findViewById(R.id.filter_spinner_source);
        Spinner spContentType = dialogView.findViewById(R.id.filter_spinner_content_type);
        Spinner spVersion = dialogView.findViewById(R.id.filter_spinner_version);
        Spinner spLoader = dialogView.findViewById(R.id.filter_spinner_loader);
        com.google.android.material.materialswitch.MaterialSwitch swSnapshots = dialogView.findViewById(R.id.filter_switch_snapshots);

        boolean curseForgeAvailable = com.maxjubayeryt.modbundle.api.CurseForgeApi.isEnabled();
        String[] sources = {"Modrinth", "CurseForge"};
        ArrayAdapter<String> sourceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sources);
        sourceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSource.setAdapter(sourceAdapter);
        spSource.setSelection(useCurseForge ? 1 : 0);
        if (!curseForgeAvailable) spSource.setEnabled(false);

        String[] contentTypes = {"Mods", "Resource Packs", "Shaders"};
        String[] contentTypeValues = {"mod", "resourcepack", "shader"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, contentTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spContentType.setAdapter(typeAdapter);
        spContentType.setSelection(Math.max(0, Arrays.asList(contentTypeValues).indexOf(currentProjectType)));

        ArrayAdapter<String> loaderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, LOADERS);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLoader.setAdapter(loaderAdapter);
        int loaderIdx = Arrays.asList(LOADERS).indexOf(currentLoader.isEmpty() ? "Any" : currentLoader);
        spLoader.setSelection(Math.max(0, loaderIdx));

        swSnapshots.setChecked(includeSnapshots);

        // Version list depends on the snapshot toggle, so it's (re)loaded both on open and
        // whenever the switch flips, keeping the currently selected version if it's still
        // present in the new list.
        api.getGameVersions(includeSnapshots, versions -> handler.post(() -> {
            ArrayAdapter<String> vAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, versions);
            vAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spVersion.setAdapter(vAdapter);
            int idx = versions.indexOf(currentGameVersion.isEmpty() ? "Any" : currentGameVersion);
            if (idx >= 0) spVersion.setSelection(idx);
        }), err -> {});

        swSnapshots.setOnCheckedChangeListener((btn, checked) -> {
            api.getGameVersions(checked, versions -> handler.post(() -> {
                ArrayAdapter<String> vAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, versions);
                vAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                String previouslySelected = spVersion.getSelectedItem() != null ? spVersion.getSelectedItem().toString() : "Any";
                spVersion.setAdapter(vAdapter);
                int idx = versions.indexOf(previouslySelected);
                spVersion.setSelection(idx >= 0 ? idx : 0);
            }), err -> {});
        });

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Filters")
                .setView(dialogView)
                .setPositiveButton("Apply", (d, w) -> {
                    if (curseForgeAvailable) useCurseForge = spSource.getSelectedItemPosition() == 1;
                    currentProjectType = contentTypeValues[spContentType.getSelectedItemPosition()];
                    Object loaderSel = spLoader.getSelectedItem();
                    currentLoader = loaderSel != null ? loaderSel.toString() : "Any";
                    Object versionSel = spVersion.getSelectedItem();
                    currentGameVersion = versionSel != null ? versionSel.toString() : "Any";
                    includeSnapshots = swSnapshots.isChecked();
                    saveFilters();
                    searchMods(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveFilters() { prefs.saveFilters(getSelectedVersion(), getSelectedLoader()); }

    private Runnable pendingMetaCacheRefresh;
    /**
     * Coalesces bursts of per-mod update-check results into a single
     * notifyDataSetChanged() ~150ms after the last one arrives, instead of one full
     * RecyclerView rebind per result (see checkUpdates()).
     */
    private void scheduleMetaCacheRefresh() {
        if (pendingMetaCacheRefresh != null) handler.removeCallbacks(pendingMetaCacheRefresh);
        pendingMetaCacheRefresh = () -> { installedAdapter.notifyDataSetChanged(); pendingMetaCacheRefresh = null; };
        handler.postDelayed(pendingMetaCacheRefresh, 150);
    }
    private String getSelectedVersion() {
        return "Any".equalsIgnoreCase(currentGameVersion) ? "" : currentGameVersion;
    }
    private String getSelectedLoader() {
        return "Any".equalsIgnoreCase(currentLoader) ? "" : currentLoader.toLowerCase();
    }
}
