package com.maxjubayeryt.modbundle.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.maxjubayeryt.modbundle.R;
import com.maxjubayeryt.modbundle.utils.ModIconLoader;
import com.maxjubayeryt.modbundle.utils.ModMetadata;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstalledModsAdapter extends RecyclerView.Adapter<InstalledModsAdapter.ViewHolder> {

    public interface OnDeleteListener { void onDelete(Object mod); }
    public interface OnDisableListener { void onDisable(Object mod); }
    public interface OnUpdateListener { void onUpdate(Object mod, ModMetadata meta); }
    public interface OnSwitchVersionListener { void onSwitchVersion(Object mod); }

    private final List<Object> mods;
    private final OnDeleteListener deleteListener;
    private final OnDisableListener disableListener;
    private final OnUpdateListener updateListener;
    private OnSwitchVersionListener switchVersionListener;
    private boolean showDisable = true;
    private boolean showCheckboxes = false;
    private String currentType = "mods";
    // Used by ContentNameResolver to look up an already-known project id for a filename
    // without re-hashing the file — see setInstalledIndex().
    private String instanceKey;
    private com.maxjubayeryt.modbundle.utils.InstalledIndex installedIndex;

    // Cache metadata per filename
    private final Map<String, ModMetadata> metaCache = new HashMap<>();
    // Filenames currently being updated — shown with an inline spinner instead of the
    // update button, instead of blocking the whole screen with a dialog per update.
    private final java.util.Set<String> updatingFiles = new java.util.HashSet<>();
    // Track selected items
    private final List<Object> selectedMods = new ArrayList<>();

    public InstalledModsAdapter(List<Object> mods, OnDeleteListener deleteListener,
                                 OnDisableListener disableListener, OnUpdateListener updateListener) {
        this.mods = mods;
        this.deleteListener = deleteListener;
        this.disableListener = disableListener;
        this.updateListener = updateListener;
    }

    public void setOnSwitchVersionListener(OnSwitchVersionListener listener) { this.switchVersionListener = listener; }

    /** Supplies what ContentNameResolver needs to look up an already-known project title. */
    public void setInstalledIndex(com.maxjubayeryt.modbundle.utils.InstalledIndex index, String instanceKey) {
        this.installedIndex = index;
        this.instanceKey = instanceKey;
    }

    /** Toggles the inline per-row update spinner for a filename, in place of the update button. */
    public void setUpdating(String filename, boolean updating) {
        if (updating) updatingFiles.add(filename); else updatingFiles.remove(filename);
        int index = -1;
        for (int i = 0; i < mods.size(); i++) {
            Object m = mods.get(i);
            String name = (m instanceof File) ? ((File) m).getName() : (m instanceof DocumentFile) ? ((DocumentFile) m).getName() : null;
            if (filename.equals(name)) { index = i; break; }
        }
        if (index >= 0) notifyItemChanged(index);
    }


    public void setShowDisable(boolean show) { this.showDisable = show; }
    public void setCurrentType(String type) { this.currentType = type; }
    public void setShowCheckboxes(boolean show) { this.showCheckboxes = show; selectedMods.clear(); notifyDataSetChanged(); }
    public List<Object> getSelectedMods() { return new ArrayList<>(selectedMods); }
    public void selectAll() { selectedMods.clear(); selectedMods.addAll(mods); notifyDataSetChanged(); }
    public void deselectAll() { selectedMods.clear(); notifyDataSetChanged(); }
    public void updateMetaCache(String filename, ModMetadata meta) {
        metaCache.put(filename, meta);
        notifyDataSetChanged();
    }
    public Map<String, ModMetadata> getMetaCache() { return metaCache; }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_installed_mod, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object mod = mods.get(position);
        String name = "";
        long size = 0;

        if (mod instanceof DocumentFile) {
            DocumentFile df = (DocumentFile) mod;
            name = df.getName() != null ? df.getName() : "";
            size = df.length();
        } else if (mod instanceof File) {
            File f = (File) mod;
            name = f.getName();
            size = f.length();
        }

        // Checkbox
        holder.checkbox.setVisibility(showCheckboxes ? View.VISIBLE : View.GONE);
        final Object modRef = mod;
        final String modName = name;
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(selectedMods.contains(mod));
        holder.checkbox.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) { if (!selectedMods.contains(modRef)) selectedMods.add(modRef); }
            else selectedMods.remove(modRef);
        });

        // Icon
        holder.icon.setImageResource(R.drawable.ic_mod_default);
        holder.icon.setTag(name);
        ModIconLoader.FileType fileType = getFileType();
        final String tagName = name;
        final android.widget.ImageView iconView = holder.icon;
        if (mod instanceof DocumentFile) {
            ModIconLoader.load(iconView.getContext(), (DocumentFile) mod, fileType, new android.widget.ImageView(iconView.getContext()) {
                public void setImageBitmap(android.graphics.Bitmap bm) { if (tagName.equals(iconView.getTag())) iconView.setImageBitmap(bm); }
                public void setImageResource(int res) { if (tagName.equals(iconView.getTag())) iconView.setImageResource(res); }
            });
        } else if (mod instanceof File) {
            ModIconLoader.load(iconView.getContext(), (File) mod, fileType, new android.widget.ImageView(iconView.getContext()) {
                public void setImageBitmap(android.graphics.Bitmap bm) { if (tagName.equals(iconView.getTag())) iconView.setImageBitmap(bm); }
                public void setImageResource(int res) { if (tagName.equals(iconView.getTag())) iconView.setImageResource(res); }
            });
        }

        holder.name.setText(name); // immediate placeholder — replaced below once resolved
        holder.name.setTag(name);
        final String nameTagAtBind = name;
        final android.widget.TextView nameView = holder.name;
        com.maxjubayeryt.modbundle.utils.ContentNameResolver.resolve(
                nameView.getContext(), mod, name, instanceKey, installedIndex,
                resolvedName -> { if (nameTagAtBind.equals(nameView.getTag())) nameView.setText(resolvedName); });
        holder.size.setText(formatSize(size));

        boolean isDisabled = name.endsWith(".disabled");
        String ext = isDisabled ? ".disabled" : name.endsWith(".jar") ? ".jar" : ".zip";
        holder.typeBadge.setText(ext);
        holder.typeBadge.setTextColor(isDisabled ? com.maxjubayeryt.modbundle.utils.ThemeColors.onSurfaceVariant(holder.typeBadge) : com.maxjubayeryt.modbundle.utils.ThemeColors.success(holder.typeBadge));
        holder.itemView.setAlpha(isDisabled ? 0.5f : 1f);

        // Update badge — shows an inline spinner in place of the button while this
        // specific file's update download is running, instead of a blocking dialog.
        ModMetadata meta = metaCache.get(name);
        boolean isUpdating = updatingFiles.contains(name);
        if (isUpdating) {
            holder.btnUpdate.setVisibility(View.GONE);
            holder.progressUpdate.setVisibility(View.VISIBLE);
        } else if (meta != null && meta.hasUpdate) {
            holder.progressUpdate.setVisibility(View.GONE);
            holder.btnUpdate.setVisibility(View.VISIBLE);
            holder.btnUpdate.setOnClickListener(v -> { if (updateListener != null) updateListener.onUpdate(modRef, meta); });
        } else {
            holder.progressUpdate.setVisibility(View.GONE);
            holder.btnUpdate.setVisibility(View.GONE);
        }

        // On/off toggle — replaces the old pause/play icon button, and now shown for
        // every content type (mods, resource packs, shaders), not just mods.
        holder.switchEnabled.setVisibility(showDisable ? View.VISIBLE : View.GONE);
        holder.switchEnabled.setOnCheckedChangeListener(null); // avoid firing while we set the state below
        holder.switchEnabled.setChecked(!isDisabled);
        holder.switchEnabled.setOnCheckedChangeListener((btn, checkedOn) -> {
            if (disableListener != null) disableListener.onDisable(modRef);
        });

        holder.btnDelete.setOnClickListener(v -> deleteListener.onDelete(modRef));

        // Switch version button
        holder.btnSwitchVersion.setOnClickListener(v -> {
            if (switchVersionListener != null) switchVersionListener.onSwitchVersion(modRef);
        });
    }

    private ModIconLoader.FileType getFileType() {
        if ("shaderpacks".equals(currentType)) return ModIconLoader.FileType.SHADER;
        if ("resourcepacks".equals(currentType)) return ModIconLoader.FileType.RESOURCEPACK;
        return ModIconLoader.FileType.MOD;
    }

    @Override public int getItemCount() { return mods.size(); }

    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / (1024f * 1024f));
        return String.format("%.1f KB", bytes / 1024f);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkbox;
        android.widget.ImageView icon;
        TextView name, size, typeBadge;
        ImageButton btnDelete, btnUpdate, btnSwitchVersion;
        com.google.android.material.materialswitch.MaterialSwitch switchEnabled;
        com.google.android.material.progressindicator.CircularProgressIndicator progressUpdate;
        ViewHolder(View v) {
            super(v);
            checkbox = v.findViewById(R.id.mod_checkbox);
            icon = v.findViewById(R.id.mod_icon);
            name = v.findViewById(R.id.mod_filename);
            size = v.findViewById(R.id.mod_size);
            typeBadge = v.findViewById(R.id.mod_type_badge);
            btnDelete = v.findViewById(R.id.btn_delete_mod);
            btnUpdate = v.findViewById(R.id.btn_update_mod);
            btnSwitchVersion = v.findViewById(R.id.btn_switch_version);
            progressUpdate = v.findViewById(R.id.progress_update_mod);
            switchEnabled = v.findViewById(R.id.switch_enabled_mod);
        }
    }
}
